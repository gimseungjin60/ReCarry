package com.recarry.booking;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recarry.admin.AuditService;
import com.recarry.booking.BookingDtos.BookingRequest;
import com.recarry.booking.BookingDtos.BookingResponse;
import com.recarry.carrier.Carrier;
import com.recarry.carrier.CarrierModel;
import com.recarry.carrier.CarrierRepository;
import com.recarry.common.ApiException;
import com.recarry.common.AppProperties;
import com.recarry.common.TimeConfig;
import com.recarry.user.User;
import com.recarry.user.UserRepository;

@Service
public class BookingService {

	private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); // 헷갈리는 0 O 1 I 제외
	private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

	private final BookingRepository bookings;
	private final CarrierRepository carriers;
	private final UserRepository users;
	private final BookingDates dates;
	private final Clock clock;
	private final AppProperties props;
	private final AuditService audit;
	private final BookingMails mails;
	private final Duration requestTtl;
	private final SecureRandom random = new SecureRandom();

	public BookingService(BookingRepository bookings, CarrierRepository carriers, UserRepository users,
			BookingDates dates, Clock clock, AppProperties props, AuditService audit, BookingMails mails) {
		this.mails = mails;
		this.bookings = bookings;
		this.carriers = carriers;
		this.users = users;
		this.dates = dates;
		this.clock = clock;
		this.props = props;
		this.audit = audit;
		this.requestTtl = Duration.ofDays(props.booking().requestExpiryDays());
	}

	/**
	 * 예약 요청 생성.
	 * 1) 날짜 검증 → 2) 사이즈에서 기간이 비어 있는 캐리어 후보 (원하는 Carrier ID 가 있으면 먼저)
	 * → 3) 후보 행을 잠근 뒤 다시 점유 확인 → 4) 서버 금액 계산 · 저장.
	 * 동시에 같은 캐리어를 노려도 행 잠금이 한 줄로 세우고, 마지막으로 DB 배타 제약이 막는다.
	 */
	@Transactional
	public BookingResponse create(Long userId, BookingRequest req) {
		int nights = dates.validate(req.startDate(), req.endDate());
		User user = users.findById(userId)
			.orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
		// 메일을 보낼 수 있는 환경에서는 이메일 인증을 마친 회원만 예약한다 (관리자 제외)
		if (user.needsEmailVerification(props.mail())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "이메일 인증을 마친 뒤 예약할 수 있습니다. 메일함을 확인해주세요.");
		}
		if (user.needsConsent(props.legal())) {
			// 바뀐 약관·개인정보 처리방침에 동의해야 예약할 수 있다 (POST /api/users/me/consent)
			throw new ApiException(HttpStatus.FORBIDDEN, "CONSENT_REQUIRED", "변경된 이용약관과 개인정보 처리방침에 동의해주세요.");
		}

		List<Long> candidates = new ArrayList<>(carriers.findFreeIds(req.size(), req.startDate(), req.endDate()));
		if (req.carrierCode() != null && !req.carrierCode().isBlank()) {
			Carrier wanted = carriers.findByCode(req.carrierCode())
				.orElseThrow(() -> ApiException.notFound("CARRIER_NOT_FOUND", "캐리어를 찾을 수 없습니다."));
			if (!wanted.getModel().getSize().equals(req.size())) {
				throw ApiException.badRequest("CARRIER_SIZE_MISMATCH", "선택한 사이즈의 캐리어가 아닙니다.");
			}
			// 원하는 캐리어가 비어 있으면 그것부터. 아니면 같은 사이즈의 다른 캐리어로 배정한다
			if (candidates.remove(wanted.getId())) candidates.add(0, wanted.getId());
		} else if (candidates.isEmpty() && !sizeExists(req.size())) {
			throw ApiException.notFound("CARRIER_NOT_FOUND", "해당 사이즈의 캐리어가 없습니다.");
		}

		for (Long id : candidates) {
			Carrier carrier = carriers.lockById(id).orElse(null);
			// 후보를 고른 뒤 다른 예약이 들어왔거나 관리자가 운영 상태를 바꿨을 수 있다 — 잠근 뒤 다시 본다
			if (carrier == null || !carriers.isBookable(id, req.startDate(), req.endDate())) continue;

			CarrierModel model = carrier.getModel();
			Pricing.Quote quote = Pricing.quote(model.getPrice(), model.getExtraNightPrice(), nights);
			if (req.expectedTotal() != null && req.expectedTotal() != quote.total()) {
				throw ApiException.conflict("PRICE_MISMATCH",
					"요금이 변경되었습니다. 다시 확인해주세요. (" + quote.total() + "원)");
			}

			Booking booking = new Booking(newNumber(), user, carrier, req.startDate(), req.endDate(), nights,
				req.recipientName().trim(), req.phone().trim(), req.address().trim(), blankIfNull(req.addressDetail()),
				blankIfNull(req.requestMessage()), quote, Pricing.deposit(quote.total(), props.booking().depositPercent()));
			bookings.saveAndFlush(booking);
			mails.received(booking);
			return respond(booking, false);
		}
		throw ApiException.conflict("CARRIER_UNAVAILABLE", "선택한 기간에 예약할 수 있는 캐리어가 없습니다.");
	}

	@Transactional(readOnly = true)
	public List<BookingResponse> listMine(Long userId) {
		return bookings.findMine(userId).stream().map(b -> respond(b, false)).toList();
	}

	@Transactional(readOnly = true)
	public BookingResponse getMine(Long userId, String number) {
		return respond(findMine(userId, number), false);
	}

	/**
	 * 사용자 취소 — 입금 전 요청은 무료, 입금 후에는 배송 예정일 전날까지 대여 정책 기준으로 환불 (Refunds).
	 * 배송이 시작된 뒤에는 회수가 필요해 고객센터로 문의한다.
	 */
	@Transactional
	public BookingResponse cancelMine(Long userId, String number) {
		Booking b = bookings.lockByNumber(number).filter(x -> x.getUser().getId().equals(userId))
			.orElseThrow(() -> ApiException.notFound("BOOKING_NOT_FOUND", "예약을 찾을 수 없습니다."));
		LocalDate today = TimeConfig.today(clock, props);
		if (!Refunds.customerCanCancel(b, today)) {
			throw ApiException.conflict("NOT_CANCELLABLE", "배송이 시작되었거나 끝난 예약은 직접 취소할 수 없습니다. 고객센터로 문의해주세요.");
		}
		if (b.isPaid()) b.setRefund(Refunds.customerCancelRefund(b, clock.instant(), today));
		b.moveTo(BookingStatus.CANCELLED, clock.instant(), Booking.CancelReason.USER);
		mails.cancelled(b);
		return respond(b, false);
	}

	// ---------------------------------------------------------------- admin

	@Transactional(readOnly = true)
	public List<BookingResponse> listAll(BookingStatus status) {
		return bookings.findAllForAdmin(status).stream().map(b -> respond(b, true)).toList();
	}

	@Transactional(readOnly = true)
	public BookingResponse getAny(String number) {
		return respond(findAny(number), true);
	}

	/**
	 * 운영자 상태 변경. 예약 요청 → 확정은 입금 확인(confirmPayment)으로만 한다.
	 * 입금한 예약을 취소하면 refundAmount(없으면 입금액 전액)를 환불 금액으로 남긴다 — 회사 사정 배상은 별도.
	 */
	@Transactional
	public BookingResponse changeStatus(Long adminId, String number, BookingStatus next, Integer refundAmount) {
		Booking b = lock(number);
		BookingStatus before = b.getStatus();
		if (before == BookingStatus.REQUESTED && next == BookingStatus.CONFIRMED) {
			throw ApiException.conflict("USE_PAYMENT_CONFIRM", "예약 확정은 입금 확인으로 해주세요.");
		}
		String detail = before + " → " + next;
		if (next == BookingStatus.CANCELLED && b.isPaid() && before.canMoveTo(next)) {
			int refund = refundAmount == null ? b.getPaidAmount() : refundAmount;
			if (refund > b.getPaidAmount()) throw ApiException.badRequest("INVALID_REFUND", "환불 금액이 입금액보다 클 수 없습니다.");
			b.setRefund(refund);
			detail += " · 환불 " + refund + "원";
		}
		// 결제 전 요청을 운영자가 취소하면 "거절" — 확정 이후 취소와 구분한다
		b.moveTo(next, clock.instant(),
			before == BookingStatus.REQUESTED ? Booking.CancelReason.REJECTED : Booking.CancelReason.ADMIN);
		audit.record(adminId, "BOOKING_STATUS", number, detail);
		if (next == BookingStatus.CANCELLED) mails.cancelled(b);
		return respond(b, true);
	}

	/** 무통장 입금 확인 → 예약 확정. 입금액은 결제 예정 금액(대여 요금 + 보증금)과 같아야 한다 */
	@Transactional
	public BookingResponse confirmPayment(Long adminId, String number, int amount, String payerName) {
		Booking b = lock(number);
		if (b.getStatus() != BookingStatus.REQUESTED) {
			throw ApiException.conflict("NOT_AWAITING_PAYMENT", "입금 대기 중인 예약이 아닙니다.");
		}
		if (amount != b.amountDue()) {
			throw ApiException.badRequest("AMOUNT_MISMATCH", "입금액이 결제 예정 금액(" + b.amountDue() + "원)과 다릅니다. 확인 후 다시 입력해주세요.");
		}
		Instant now = clock.instant();
		b.markPaid(amount, payerName.trim(), now);
		b.moveTo(BookingStatus.CONFIRMED, now, null);
		audit.record(adminId, "BOOKING_PAYMENT", number, "입금 확인 " + amount + "원 · 입금자 " + payerName.trim());
		mails.confirmed(b);
		return respond(b, true);
	}

	/** 취소 환불을 송금했다 */
	@Transactional
	public BookingResponse markRefunded(Long adminId, String number) {
		Booking b = lock(number);
		if (b.getStatus() != BookingStatus.CANCELLED || b.getRefundAmount() == null || b.getRefundAmount() == 0) {
			throw ApiException.conflict("NO_REFUND", "환불할 금액이 있는 취소 예약이 아닙니다.");
		}
		if (b.getRefundedAt() != null) throw ApiException.conflict("ALREADY_REFUNDED", "이미 환불 완료로 처리했습니다.");
		b.markRefunded(clock.instant());
		audit.record(adminId, "BOOKING_REFUND", number, "환불 완료 " + b.getRefundAmount() + "원");
		mails.refunded(b);
		return respond(b, true);
	}

	/** 반납 검수 후 보증금 정산 — 공제액(파손 · 연체)을 빼고 돌려준 것으로 기록한다 */
	@Transactional
	public BookingResponse settleDeposit(Long adminId, String number, int deduction, String note) {
		Booking b = lock(number);
		if (b.getStatus() != BookingStatus.RETURNED || !b.isPaid()) {
			throw ApiException.conflict("NOT_RETURNED", "반납이 끝난 결제 예약만 보증금을 정산할 수 있습니다.");
		}
		if (b.getDepositSettledAt() != null) throw ApiException.conflict("ALREADY_SETTLED", "이미 정산했습니다.");
		if (deduction > b.getDeposit()) {
			throw ApiException.badRequest("INVALID_DEDUCTION", "공제액이 보증금(" + b.getDeposit() + "원)보다 클 수 없습니다. 초과분은 따로 청구해주세요.");
		}
		String n = note == null ? "" : note.trim();
		if (deduction > 0 && n.isEmpty()) throw ApiException.badRequest("INVALID_DEDUCTION", "공제 사유를 입력해주세요.");
		b.settleDeposit(deduction, n, clock.instant());
		audit.record(adminId, "BOOKING_DEPOSIT", number, "보증금 " + b.getDeposit() + "원 · 공제 " + deduction + "원");
		mails.depositSettled(b);
		return respond(b, true);
	}

	private Booking lock(String number) {
		return bookings.lockByNumber(number)
			.orElseThrow(() -> ApiException.notFound("BOOKING_NOT_FOUND", "예약을 찾을 수 없습니다."));
	}

	/**
	 * 탈퇴 전 정리: 진행 중(확정 · 이용 중) 예약이 있으면 탈퇴할 수 없다.
	 * 예약 요청은 취소하고, 결제 전 단계에서 끝난 예약(고객 취소 · 기한 만료)의 배송 정보는 지운다.
	 * 확정 이후의 예약 기록은 거래 기록 보관 의무 때문에 남긴다 (docs/POLICY.md).
	 */
	@Transactional
	public void closeForWithdrawal(Long userId) {
		List<Booking> mine = bookings.lockAllOf(userId);
		if (mine.stream().anyMatch(b -> b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.IN_USE)) {
			throw ApiException.conflict("HAS_ACTIVE_BOOKING",
				"확정되었거나 이용 중인 예약이 있어 탈퇴할 수 없습니다. 반납이 끝난 뒤 다시 시도해주세요.");
		}
		for (Booking b : mine) {
			if (b.getStatus() == BookingStatus.REQUESTED) b.moveTo(BookingStatus.CANCELLED, clock.instant(), Booking.CancelReason.USER);
			if (b.getStatus() == BookingStatus.CANCELLED && b.getCancelReason() != null
					&& b.getCancelReason().beforePayment()) {
				b.eraseRecipient();
			}
		}
	}

	// ----------------------------------------------------------------

	private Booking findMine(Long userId, String number) {
		// 다른 사람의 예약도 "없음"으로 답한다 (존재 여부를 흘리지 않는다)
		return bookings.findMine(number, userId)
			.orElseThrow(() -> ApiException.notFound("BOOKING_NOT_FOUND", "예약을 찾을 수 없습니다."));
	}

	private Booking findAny(String number) {
		return bookings.findByNumber(number)
			.orElseThrow(() -> ApiException.notFound("BOOKING_NOT_FOUND", "예약을 찾을 수 없습니다."));
	}

	private BookingResponse respond(Booking b, boolean withCustomer) {
		return BookingResponse.of(b, withCustomer, new BookingDtos.View(requestTtl, props.booking().expiryAppliesFrom(),
			clock.instant(), TimeConfig.today(clock, props), props.payment()));
	}

	private boolean sizeExists(String size) {
		return carriers.findAllWithModel().stream().anyMatch(c -> c.getModel().getSize().equals(size));
	}

	/** RB-260925-7KQ4ZP 형식. 날짜 + 임의 6자리, 충돌하면 다시 뽑는다. */
	private String newNumber() {
		LocalDate today = TimeConfig.today(clock, props);
		for (int attempt = 0; attempt < 5; attempt++) {
			StringBuilder sb = new StringBuilder("RB-").append(today.format(YYMMDD)).append('-');
			for (int i = 0; i < 6; i++) sb.append(CODE_CHARS[random.nextInt(CODE_CHARS.length)]);
			String number = sb.toString();
			if (!bookings.existsByBookingNumber(number)) return number;
		}
		throw new IllegalStateException("could not allocate booking number");
	}

	private static String blankIfNull(String s) {
		return s == null ? "" : s.trim();
	}
}
