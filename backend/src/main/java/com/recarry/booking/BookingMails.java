package com.recarry.booking;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.recarry.common.AppProperties;
import com.recarry.common.Mailer;

/**
 * 예약 알림 메일 — 접수(입금 안내) · 확정 · 취소(환불 안내) · 환불 완료 · 보증금 정산.
 * 커밋 뒤 백그라운드로 보낸다 (Mailer.sendAfterCommit). 메일을 못 보내도 예약 처리는 그대로 진행된다.
 */
@Component
public class BookingMails {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREAN);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

	private final Mailer mailer;
	private final AppProperties props;
	private final ZoneId zone;

	public BookingMails(Mailer mailer, AppProperties props) {
		this.mailer = mailer;
		this.props = props;
		this.zone = props.booking().zone();
	}

	/** 입금 안내에 쓰는 입금자명: 받는 분 이름 + 예약번호 끝 4자리 (통장에서 예약을 찾기 쉽게) */
	public static String payerHint(Booking b) {
		String n = b.getBookingNumber();
		return b.getRecipientName() + n.substring(n.length() - 4);
	}

	public void received(Booking b) {
		AppProperties.Payment p = props.payment();
		Duration ttl = Duration.ofDays(props.booking().requestExpiryDays());
		String deadline = b.getCreatedAt() == null ? "접수 후 " + props.booking().requestExpiryDays() + "일"
			: TIME.format(b.getCreatedAt().plus(ttl).atZone(zone));
		send(b, "[RECARRY] 예약 요청이 접수되었습니다 — 입금 안내",
			"예약 요청이 접수되었습니다. 아래 계좌로 입금하시면 확인 후 예약이 확정됩니다.\n\n"
				+ summary(b)
				+ "\n입금 계좌: " + p.bank() + " " + p.account() + " (" + p.holder() + ")\n"
				+ "입금 금액: " + won(b.amountDue()) + " (대여 요금 " + won(b.getTotalPrice()) + " + 보증금 " + won(b.getDeposit()) + ")\n"
				+ "입금자명: " + payerHint(b) + " (받는 분 이름 + 예약번호 끝 4자리)\n"
				+ "입금 기한: " + deadline + " — 기한까지 확인되지 않으면 자동으로 취소됩니다.\n"
				+ (p.sample() ? "\n※ 지금은 임시(테스트) 계좌입니다. 실제로 입금하지 마세요.\n" : ""));
	}

	public void confirmed(Booking b) {
		send(b, "[RECARRY] 입금이 확인되어 예약이 확정되었습니다",
			"입금이 확인되어 예약이 확정되었습니다.\n\n" + summary(b)
				+ "\n캐리어는 " + DATE.format(b.getStartDate().minusDays(1)) + "까지 문 앞으로 도착하고, "
				+ DATE.format(b.getEndDate()) + "에 같은 주소에서 회수합니다.\n"
				+ "배송 예정일 3일 전까지 My Page 에서 취소하면 전액 환불됩니다.\n");
	}

	public void cancelled(Booking b) {
		String why = switch (b.getCancelReason()) {
			case EXPIRED -> "입금 기한까지 입금이 확인되지 않아 예약 요청이 자동으로 취소되었습니다.";
			case REJECTED -> "예약 요청을 확정하지 못해 취소되었습니다. 이용에 불편을 드려 죄송합니다.";
			case ADMIN -> "예약이 취소되었습니다.";
			case USER -> "요청하신 대로 예약이 취소되었습니다.";
		};
		Integer refund = b.getRefundAmount();
		send(b, "[RECARRY] 예약이 취소되었습니다",
			why + "\n\n" + summary(b)
				+ (b.isPaid() ? "\n환불 금액: " + won(refund == null ? 0 : refund) + " — 담당자가 휴대폰으로 환불 계좌를 확인한 뒤 영업일 3일 안에 보내드립니다.\n" : ""));
	}

	public void refunded(Booking b) {
		send(b, "[RECARRY] 환불이 완료되었습니다",
			"취소하신 예약의 환불금 " + won(b.getRefundAmount()) + "을 송금했습니다.\n\n" + summary(b));
	}

	public void depositSettled(Booking b) {
		int back = b.getDeposit() - b.getDepositDeduction();
		String note = b.getDepositNote() == null || b.getDepositNote().isBlank() ? "" : "공제 사유: " + b.getDepositNote() + "\n";
		send(b, "[RECARRY] 보증금 정산이 완료되었습니다",
			"반납 검수가 끝나 보증금을 정산했습니다.\n\n" + summary(b)
				+ "\n보증금 " + won(b.getDeposit()) + " - 공제 " + won(b.getDepositDeduction()) + " = 돌려드릴 금액 " + won(back) + "\n"
					+ (back > 0 ? "담당자가 휴대폰으로 반환 계좌를 확인한 뒤 영업일 3일 안에 보내드립니다.\n" : "")
				+ note);
	}

	private String summary(Booking b) {
		return "예약번호: " + b.getBookingNumber() + "\n"
			+ "캐리어: " + b.getCarrier().getModel().getName() + " · " + b.getCarrier().getCode() + "\n"
			+ "여행 날짜: " + DATE.format(b.getStartDate()) + " — " + DATE.format(b.getEndDate()) + " (" + b.getNights() + "박)\n"
			+ "예약 확인: " + props.appUrl() + "/mypage/bookings/" + b.getBookingNumber() + "\n";
	}

	private void send(Booking b, String subject, String body) {
		var user = b.getUser();
		if (user.getDeletedAt() != null) return;   // 탈퇴한 회원에게는 보내지 않는다
		mailer.sendAfterCommit(user.getEmail(), subject, body + "\n- RECARRY (발신 전용 메일입니다)");
	}

	private static String won(int n) {
		return String.format("%,d원", n);
	}
}
