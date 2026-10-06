package com.recarry.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.recarry.booking.BookingDtos.BookingResponse;
import com.recarry.booking.BookingDtos.StatusChangeRequest;
import com.recarry.booking.BookingService;
import com.recarry.booking.BookingStatus;
import com.recarry.carrier.CarrierAdminService;
import com.recarry.carrier.CarrierDtos.CarrierCreateRequest;
import com.recarry.carrier.CarrierDtos.CarrierResponse;
import com.recarry.carrier.CarrierDtos.CarrierUpdateRequest;
import com.recarry.carrier.CarrierDtos.EventRequest;
import com.recarry.carrier.CarrierDtos.InspectionRequest;
import com.recarry.carrier.CarrierDtos.ModelUpdateRequest;
import com.recarry.carrier.CarrierStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** /api/admin/** — SecurityConfig 가 ADMIN 만 통과시킨다. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final CarrierAdminService carriers;
	private final BookingService bookings;

	public AdminController(CarrierAdminService carriers, BookingService bookings) {
		this.carriers = carriers;
		this.bookings = bookings;
	}

	// ---------------------------------------------------------------- carriers

	/** 전체 캐리어 + 오늘 기준 표시 상태. 재고 요약은 이 목록에서 계산한다. 상세는 공개 GET /api/carriers/{code}. */
	@GetMapping("/carriers")
	public List<CarrierResponse> carriers() {
		return carriers.list();
	}

	@PostMapping("/carriers")
	@ResponseStatus(HttpStatus.CREATED)
	public CarrierResponse createCarrier(@Valid @RequestBody CarrierCreateRequest req) {
		return carriers.create(req);
	}

	@PatchMapping("/carriers/{id}")
	public CarrierResponse updateCarrier(@PathVariable Long id, @Valid @RequestBody CarrierUpdateRequest req) {
		return carriers.update(id, req);
	}

	public record CarrierStatusRequest(@NotNull(message = "상태를 선택해주세요.") CarrierStatus status) {}

	@PatchMapping("/carriers/{id}/status")
	public CarrierResponse changeCarrierStatus(@PathVariable Long id, @Valid @RequestBody CarrierStatusRequest req) {
		return carriers.changeStatus(id, req.status());
	}

	@PostMapping("/carriers/{id}/inspection")
	public CarrierResponse recordInspection(@PathVariable Long id, @Valid @RequestBody InspectionRequest req) {
		return carriers.recordInspection(id, req);
	}

	@PostMapping("/carriers/{id}/events")
	@ResponseStatus(HttpStatus.CREATED)
	public CarrierResponse addEvent(@PathVariable Long id, @Valid @RequestBody EventRequest req) {
		return carriers.addEvent(id, req);
	}

	@DeleteMapping("/carriers/{id}/events/{eventId}")
	public CarrierResponse removeEvent(@PathVariable Long id, @PathVariable Long eventId) {
		return carriers.removeEvent(id, eventId);
	}

	/** 사이즈 상품의 가격 · 문구. 조회는 공개 GET /api/carriers. */
	@PatchMapping("/carrier-models/{size}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void updateModel(@PathVariable String size, @Valid @RequestBody ModelUpdateRequest req) {
		carriers.updateModel(size, req);
	}

	// ---------------------------------------------------------------- bookings

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
