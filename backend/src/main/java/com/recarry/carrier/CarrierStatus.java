package com.recarry.carrier;

/**
 * 캐리어 상태.
 * 저장하는 것은 운영 상태(AVAILABLE · INSPECTION · REPAIR · UNAVAILABLE) 뿐이다.
 * RESERVED · RENTED 는 날짜마다 다르므로 예약에서 계산해 응답에만 쓴다.
 */
public enum CarrierStatus {
	AVAILABLE,
	INSPECTION,
	REPAIR,
	UNAVAILABLE,
	/** 계산값: 오늘을 포함하는 REQUESTED/CONFIRMED 예약이 있다 */
	RESERVED,
	/** 계산값: 오늘 IN_USE 예약이 있다 */
	RENTED;

	public boolean operational() {
		return this != RESERVED && this != RENTED;
	}
}
