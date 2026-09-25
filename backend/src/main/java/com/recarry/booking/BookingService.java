package com.recarry.booking;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	private final SecureRandom random = new SecureRandom();

	public BookingService(BookingRepository bookings, CarrierRepository carriers, UserRepository users,
			BookingDates dates, Clock clock, AppProperties props) {
		this.bookings = bookings;
		this.carriers = carriers;
		this.users = users;
		this.dates = dates;
		this.clock = clock;
		this.props = props;
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
			if (carrier == null || carriers.isOccupied(id, req.startDate(), req.endDate())) continue;

			CarrierModel model = carrier.getModel();
			Pricing.Quote quote = Pricing.quote(model.getPrice(), model.getExtraNightPrice(), nights);
			if (req.expectedTotal() != null && req.expectedTotal() != quote.total()) {
				throw ApiException.conflict("PRICE_MISMATCH",
					"요금이 변경되었습니다. 다시 확인해주세요. (" + quote.total() + "원)");
			}

			Booking booking = new Booking(newNumber(), user, carrier, req.startDate(), req.endDate(), nights,
				req.recipientName().trim(), req.phone().trim(), req.address().trim(), blankIfNull(req.addressDetail()),
				blankIfNull(req.requestMessage()), quote);
			bookings.saveAndFlush(booking);
			return BookingResponse.of(booking, false);
		}
		throw ApiException.conflict("CARRIER_UNAVAILABLE", "선택한 기간에 예약할 수 있는 캐리어가 없습니다.");
	}

	@Transactional(readOnly = true)
	public List<BookingResponse> listMine(Long userId) {
		return bookings.findMine(userId).stream().map(b -> BookingResponse.of(b, false)).toList();
	}

	@Transactional(readOnly = true)
	public BookingResponse getMine(Long userId, String number) {
		return BookingResponse.of(findMine(userId, number), false);
	}

	/** 사용자 취소 — REQUESTED 만. 환불은 없다 (정책 미정). */
	@Transactional
	public BookingResponse cancelMine(Long userId, String number) {
		Booking b = findMine(userId, number);
		if (!b.getStatus().userCancellable()) {
			throw ApiException.conflict("NOT_CANCELLABLE", "이 예약은 직접 취소할 수 없습니다. 고객센터로 문의해주세요.");
		}
		b.moveTo(BookingStatus.CANCELLED, clock.instant());
		return BookingResponse.of(b, false);
	}

	// ---------------------------------------------------------------- admin

	@Transactional(readOnly = true)
	public List<BookingResponse> listAll(BookingStatus status) {
		return bookings.findAllForAdmin(status).stream().map(b -> BookingResponse.of(b, true)).toList();
	}

	@Transactional(readOnly = true)
	public BookingResponse getAny(String number) {
		return BookingResponse.of(findAny(number), true);
	}

	@Transactional
	public BookingResponse changeStatus(String number, BookingStatus next) {
		Booking b = findAny(number);
		b.moveTo(next, clock.instant());
		return BookingResponse.of(b, true);
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
