package com.recarry.booking;

import java.util.Set;

/**
 * 예약 상태와 허용 전이. 생성 직후는 REQUESTED(예약 요청 = 입금 대기), 입금 확인 후 CONFIRMED.
 * 고객이 직접 취소할 수 있는지는 날짜 · 입금 여부에 따라 다르다 (Refunds.customerCanCancel).
 *   REQUESTED → CONFIRMED | CANCELLED
 *   CONFIRMED → IN_USE | CANCELLED
 *   IN_USE    → RETURNED
 */
public enum BookingStatus {
	REQUESTED,
	CONFIRMED,
	IN_USE,
	RETURNED,
	CANCELLED;

	/** 이 상태의 예약만 캐리어를 점유한다 (DB 배타 제약의 WHERE 와 같다). */
	public static final Set<BookingStatus> OCCUPYING = Set.of(REQUESTED, CONFIRMED, IN_USE);

	public boolean canMoveTo(BookingStatus next) {
		return switch (this) {
			case REQUESTED -> next == CONFIRMED || next == CANCELLED;
			case CONFIRMED -> next == IN_USE || next == CANCELLED;
			case IN_USE -> next == RETURNED;
			case RETURNED, CANCELLED -> false;
		};
	}
}
