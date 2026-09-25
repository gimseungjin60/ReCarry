package com.recarry.carrier;

import java.time.LocalDate;
import java.util.List;

public final class CarrierDtos {

	private CarrierDtos() {}

	public record EventResponse(LocalDate date, CarrierEvent.Type type, String title, String detail) {
		static EventResponse of(CarrierEvent e) {
			return new EventResponse(e.getEventDate(), e.getType(), e.getTitle(), e.getDetail());
		}
	}

	/** 캐리어 한 대 (Carrier ID). status 는 오늘 기준 표시 상태. */
	public record CarrierResponse(Long id, String code, String size, String grade, CarrierStatus status,
			String collectedFrom, String repairSummary, LocalDate inspectedAt, List<EventResponse> events) {}

	/**
	 * 사이즈 단위 상품. featured 는 화면에 대표로 보여줄 캐리어,
	 * availableCount 는 운영 상태가 AVAILABLE 인 대수 (날짜 무관 — 날짜별은 availability API).
	 */
	public record CarrierModelResponse(String size, String name, String inch, String capacity, String usage,
			String dims, String weight, int price, int extraNightPrice, List<String> headline, String description,
			int availableCount, CarrierResponse featured) {}

	/** 기간 안에 예약 가능한 대수와 서버가 계산한 금액 */
	public record AvailabilityResponse(String size, int available, int nights, int totalPrice) {}
}
