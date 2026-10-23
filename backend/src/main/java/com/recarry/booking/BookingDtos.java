package com.recarry.booking;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import com.recarry.auth.AuthDtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class BookingDtos {

	private BookingDtos() {}

	/**
	 * 예약 요청. 금액은 받지 않는다 — expectedTotal 은 "사용자가 본 금액"이며
	 * 서버 계산과 다르면 예약하지 않고 409 를 돌려준다.
	 */
	public record BookingRequest(
		@NotBlank(message = "사이즈를 선택해주세요.") @Pattern(regexp = "^\\d{2}$", message = "사이즈 형식이 아닙니다.") String size,
		@Size(max = 20) String carrierCode,
		@NotNull(message = "출발일을 선택해주세요.") LocalDate startDate,
		@NotNull(message = "도착일을 선택해주세요.") LocalDate endDate,
		@NotBlank(message = "받는 분을 입력해주세요.") @Size(max = 50) String recipientName,
		@NotBlank(message = "휴대폰 번호를 입력해주세요.") @Pattern(regexp = AuthDtos.PHONE, message = "휴대폰 번호 형식이 아닙니다.") String phone,
		@NotBlank(message = "주소를 입력해주세요.") @Size(max = 200) String address,
		@Size(max = 100) String addressDetail,
		@Size(max = 300) String requestMessage,
		@PositiveOrZero Integer expectedTotal) {}

	public record StatusChangeRequest(@NotNull(message = "상태를 선택해주세요.") BookingStatus status) {}

	public record Customer(Long id, String email, String name) {}

	/**
	 * customer 는 관리자 응답에만 채운다.
	 * deposit 은 대여 요금(totalPrice)과 별도 — 결제 예정 금액은 totalPrice + deposit.
	 * expiresAt: REQUESTED 일 때만, 이 시각까지 확정되지 않으면 자동 취소된다.
	 */
	public record BookingResponse(String bookingNumber, BookingStatus status, String size, String carrierName,
			String carrierCode, String grade, LocalDate startDate, LocalDate endDate, int nights,
			LocalDate deliveryDate, LocalDate pickupDate, String recipientName, String phone, String address,
			String addressDetail, String requestMessage, int basePrice, int extraPrice, int totalPrice, int deposit,
			boolean cancellable, Instant createdAt, Instant expiresAt, Instant cancelledAt,
			Booking.CancelReason cancelReason, Customer customer) {

		static BookingResponse of(Booking b, boolean withCustomer, Duration requestTtl, Instant expiryFrom) {
			var c = b.getCarrier();
			// 자동 만료 대상(정책 시행 이후 접수된 요청)에만 기한을 보여준다 — BookingExpiry 와 같은 조건
			Instant expiresAt = b.getStatus() == BookingStatus.REQUESTED && b.getCreatedAt() != null
				&& !b.getCreatedAt().isBefore(expiryFrom) ? b.getCreatedAt().plus(requestTtl) : null;
			return new BookingResponse(b.getBookingNumber(), b.getStatus(), c.getModel().getSize(),
				c.getModel().getName(), c.getCode(), c.getGrade(), b.getStartDate(), b.getEndDate(), b.getNights(),
				b.getStartDate().minusDays(1), b.getEndDate(), b.getRecipientName(), b.getPhone(), b.getAddress(),
				b.getAddressDetail(), b.getRequestMessage(), b.getBasePrice(), b.getExtraPrice(), b.getTotalPrice(),
				b.getDeposit(), b.getStatus().userCancellable(), b.getCreatedAt(), expiresAt, b.getCancelledAt(),
				b.getCancelReason(),
				withCustomer ? new Customer(b.getUser().getId(), b.getUser().getEmail(), b.getUser().getName()) : null);
		}
	}
}
