package com.recarry.carrier;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class CarrierDtos {

	private CarrierDtos() {}

	public record EventResponse(Long id, LocalDate date, CarrierEvent.Type type, String title, String detail) {
		static EventResponse of(CarrierEvent e) {
			return new EventResponse(e.getId(), e.getEventDate(), e.getType(), e.getTitle(), e.getDetail());
		}
	}

	public record CheckResponse(InspectionCheck.Item item, boolean passed, String note) {
		static List<CheckResponse> of(List<InspectionCheck> checks) {
			return checks.stream().sorted(Comparator.comparing(InspectionCheck::getItem))
				.map(c -> new CheckResponse(c.getItem(), c.isPassed(), c.getNote())).toList();
		}
	}

	/**
	 * 캐리어 한 대 (Carrier ID). status 는 오늘 기준 표시 상태.
	 * inspection 은 항목별 검수 결과 — 비어 있으면 아직 기록이 없다.
	 */
	public record CarrierResponse(Long id, String code, String size, String grade, CarrierStatus status,
			String collectedFrom, String repairSummary, LocalDate inspectedAt, boolean featured,
			List<CheckResponse> inspection, List<EventResponse> events) {}

	/**
	 * 사이즈 단위 상품. featured 는 화면에 대표로 보여줄 캐리어,
	 * availableCount 는 운영 상태가 AVAILABLE 인 대수 (날짜 무관 — 날짜별은 availability API).
	 */
	public record CarrierModelResponse(String size, String name, String inch, String capacity, String usage,
			String dims, String weight, int price, int extraNightPrice, List<String> headline, String description,
			int availableCount, CarrierResponse featured) {}

	/** 기간 안에 예약 가능한 대수와 서버가 계산한 금액 */
	public record AvailabilityResponse(String size, int available, int nights, int totalPrice) {}

	// ---------------------------------------------------------------- admin 요청

	static final String CODE = "^[A-Z0-9][A-Z0-9-]{2,19}$";
	static final String GRADE = "^[AB]$";

	/** 새 캐리어 등록. 상태는 INSPECTION 으로 시작한다. Carrier ID · 사이즈는 등록 뒤 바꾸지 않는다. */
	public record CarrierCreateRequest(
		@NotBlank(message = "사이즈를 선택해주세요.") @Pattern(regexp = "^\\d{2}$", message = "사이즈 형식이 아닙니다.") String size,
		@NotBlank(message = "Carrier ID 를 입력해주세요.") @Pattern(regexp = CODE, message = "영문 대문자 · 숫자 · 하이픈 3~20자입니다.") String code,
		@NotBlank(message = "등급을 선택해주세요.") @Pattern(regexp = GRADE, message = "등급은 A 또는 B 입니다.") String grade,
		@NotBlank(message = "회수처를 입력해주세요.") @Size(max = 60) String collectedFrom,
		@Size(max = 120) String repairSummary,
		/** 없으면 대표가 아니다 */
		Boolean featured) {}

	public record CarrierUpdateRequest(
		@NotBlank(message = "등급을 선택해주세요.") @Pattern(regexp = GRADE, message = "등급은 A 또는 B 입니다.") String grade,
		@NotBlank(message = "회수처를 입력해주세요.") @Size(max = 60) String collectedFrom,
		@Size(max = 120) String repairSummary,
		/** 없으면 바꾸지 않는다 */
		Boolean featured) {}

	public record EventRequest(
		@NotNull(message = "날짜를 입력해주세요.") LocalDate date,
		@NotNull(message = "종류를 선택해주세요.") CarrierEvent.Type type,
		@NotBlank(message = "제목을 입력해주세요.") @Size(max = 60) String title,
		@Size(max = 160) String detail) {}

	public record CheckRequest(
		@NotNull(message = "검수 항목이 없습니다.") InspectionCheck.Item item,
		@NotNull(message = "통과 여부를 선택해주세요.") Boolean passed,
		@Size(max = 120) String note) {}

	/** 검수 한 번의 결과 — 모든 항목을 한 번씩 담는다. */
	public record InspectionRequest(
		@NotNull(message = "검수일을 입력해주세요.") LocalDate inspectedAt,
		@NotEmpty(message = "검수 항목을 입력해주세요.") List<@Valid @NotNull CheckRequest> checks) {}

	/** 사이즈 상품의 가격 · 문구. headline 줄바꿈은 '\n' */
	public record ModelUpdateRequest(
		@NotBlank @Size(max = 50) String name,
		@NotBlank @Size(max = 20) String inch,
		@NotBlank @Size(max = 20) String capacity,
		@NotBlank @Size(max = 60) String usage,
		@NotBlank @Size(max = 40) String dims,
		@NotBlank @Size(max = 20) String weight,
		@NotNull @PositiveOrZero Integer price,
		@NotNull @PositiveOrZero Integer extraNightPrice,
		@NotBlank @Size(max = 120) String headline,
		@NotBlank @Size(max = 2000) String description) {}
}
