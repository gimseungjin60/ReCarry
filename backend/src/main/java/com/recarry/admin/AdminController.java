package com.recarry.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

import com.recarry.admin.AuditService.AuditResponse;
import com.recarry.auth.CurrentUser;
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
	private final AuditService audit;

	public AdminController(CarrierAdminService carriers, BookingService bookings, AuditService audit) {
		this.carriers = carriers;
		this.bookings = bookings;
		this.audit = audit;
	}

	/** 최근 관리자 변경 기록 (최신순 100건) */
	@GetMapping("/audit-logs")
	public List<AuditResponse> auditLogs() {
		return audit.recent(100);
	}

	// ---------------------------------------------------------------- carriers

	/** 전체 캐리어 + 오늘 기준 표시 상태. 재고 요약은 이 목록에서 계산한다. 상세는 공개 GET /api/carriers/{code}. */
	@GetMapping("/carriers")
	public List<CarrierResponse> carriers() {
		return carriers.list();
	}

	@PostMapping("/carriers")
	@ResponseStatus(HttpStatus.CREATED)
	public CarrierResponse createCarrier(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CarrierCreateRequest req) {
		return carriers.create(CurrentUser.id(jwt), req);
	}

	@PatchMapping("/carriers/{id}")
	public CarrierResponse updateCarrier(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody CarrierUpdateRequest req) {
		return carriers.update(CurrentUser.id(jwt), id, req);
	}

	public record CarrierStatusRequest(@NotNull(message = "상태를 선택해주세요.") CarrierStatus status) {}

	@PatchMapping("/carriers/{id}/status")
	public CarrierResponse changeCarrierStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody CarrierStatusRequest req) {
		return carriers.changeStatus(CurrentUser.id(jwt), id, req.status());
	}

	@PostMapping("/carriers/{id}/inspection")
	public CarrierResponse recordInspection(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody InspectionRequest req) {
		return carriers.recordInspection(CurrentUser.id(jwt), id, req);
	}

	@PostMapping("/carriers/{id}/events")
	@ResponseStatus(HttpStatus.CREATED)
	public CarrierResponse addEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody EventRequest req) {
		return carriers.addEvent(CurrentUser.id(jwt), id, req);
	}

	@DeleteMapping("/carriers/{id}/events/{eventId}")
	public CarrierResponse removeEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable Long eventId) {
		return carriers.removeEvent(CurrentUser.id(jwt), id, eventId);
	}

	/** 사이즈 상품의 가격 · 문구. 조회는 공개 GET /api/carriers. */
	@PatchMapping("/carrier-models/{size}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void updateModel(@AuthenticationPrincipal Jwt jwt, @PathVariable String size, @Valid @RequestBody ModelUpdateRequest req) {
		carriers.updateModel(CurrentUser.id(jwt), size, req);
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
	public BookingResponse changeBookingStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable String bookingNumber,
			@Valid @RequestBody StatusChangeRequest req) {
		return bookings.changeStatus(CurrentUser.id(jwt), bookingNumber, req.status());
	}
}
