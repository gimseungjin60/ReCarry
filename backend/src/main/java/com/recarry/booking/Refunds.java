package com.recarry.booking;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 고객 사정 취소의 환불 계산 — 대여 정책 "고객 사정으로 취소할 때" (docs/POLICY.md · src/data/legal.ts).
 *   결제 전: 환불할 돈이 없다 (무료 취소)
 *   결제 후 24시간 이내, 또는 배송 예정일 3일 전까지: 전액 (대여 요금 + 보증금)
 *   배송 예정일 2일 전 ~ 배송 전: 대여 요금만 (보증금 = 취소 수수료)
 *   배송 이후: 온라인으로 취소할 수 없다 (회수가 필요해 고객센터 · 운영자가 처리)
 * 정책을 바꾸면 이 클래스와 legal.ts 를 같이 바꾼다.
 */
public final class Refunds {

	private Refunds() {}

	/** 고객이 직접 취소할 수 있는가 — 결제 전 요청, 또는 결제 후 배송 예정일 전날까지 */
	public static boolean customerCanCancel(Booking b, LocalDate today) {
		if (b.getStatus() == BookingStatus.REQUESTED) return true;
		return b.getStatus() == BookingStatus.CONFIRMED && today.isBefore(deliveryDate(b));
	}

	/** 지금 고객이 취소하면 돌려줄 금액 */
	public static int customerCancelRefund(Booking b, Instant now, LocalDate today) {
		if (!b.isPaid()) return 0;
		int paid = b.getPaidAmount();
		boolean within24h = !now.isAfter(b.getPaidAt().plus(Duration.ofHours(24)));
		boolean threeDaysBefore = !today.isAfter(deliveryDate(b).minusDays(3));
		if (within24h || threeDaysBefore) return paid;
		return Math.max(0, paid - b.getDeposit());
	}

	static LocalDate deliveryDate(Booking b) {
		return b.getStartDate().minusDays(1);
	}
}
