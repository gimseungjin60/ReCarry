package com.recarry.admin;

import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.recarry.booking.BookingDtos.BookingResponse;
import com.recarry.booking.BookingDtos.StatusChangeRequest;
import com.recarry.booking.BookingService;
import com.recarry.booking.BookingStatus;
import com.recarry.carrier.Carrier;
import com.recarry.carrier.CarrierDtos.CarrierResponse;
import com.recarry.carrier.CarrierRepository;
import com.recarry.carrier.CarrierService;
import com.recarry.carrier.CarrierStatus;
import com.recarry.common.ApiException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** /api/admin/** — SecurityConfig 가 ADMIN 만 통과시킨다. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final CarrierRepository carriers;
	private final CarrierService carrierService;
	private final BookingService bookings;

	public AdminController(CarrierRepository carriers, CarrierService carrierService, BookingService bookings) {
		this.carriers = carriers;
		this.carrierService = carrierService;
		this.bookings = bookings;
	}

	/** 전체 캐리어 + 오늘 기준 표시 상태. 재고 요약은 이 목록에서 계산한다. */
	@GetMapping("/carriers")
	@Transactional(readOnly = true)
	public List<CarrierResponse> carriers() {
		Map<Long, CarrierStatus> today = carrierService.statusToday();
		return carriers.findAllWithModel().stream().map(c -> CarrierService.toResponse(c, today)).toList();
	}

	public record CarrierStatusRequest(@NotNull(message = "상태를 선택해주세요.") CarrierStatus status) {}

	/** 운영 상태만 바꿀 수 있다. RESERVED / RENTED 는 예약에서 계산되는 값이다. */
	@PatchMapping("/carriers/{id}/status")
	@Transactional
	public CarrierResponse changeCarrierStatus(@PathVariable Long id, @Valid @RequestBody CarrierStatusRequest req) {
		if (!req.status().operational()) {
			throw ApiException.badRequest("INVALID_STATUS", "RESERVED / RENTED 는 예약으로 정해지는 상태입니다.");
		}
		Carrier c = carriers.findById(id)
			.orElseThrow(() -> ApiException.notFound("CARRIER_NOT_FOUND", "캐리어를 찾을 수 없습니다."));
		c.changeStatus(req.status());
		carriers.flush();
		return CarrierService.toResponse(c, carrierService.statusToday());
	}

	@GetMapping("/bookings")
	public List<BookingResponse> bookings(@RequestParam(required = false) BookingStatus status) {
		return bookings.listAll(status);
	}

	@GetMapping("/bookings/{bookingNumber}")
	public BookingResponse booking(@PathVariable String bookingNumber) {
		return bookings.getAny(bookingNumber);
	}

	@PatchMapping("/bookings/{bookingNumber}/status")
	public BookingResponse changeBookingStatus(@PathVariable String bookingNumber,
			@Valid @RequestBody StatusChangeRequest req) {
		return bookings.changeStatus(bookingNumber, req.status());
	}
}
