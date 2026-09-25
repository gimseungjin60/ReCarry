package com.recarry.booking;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.recarry.auth.CurrentUser;
import com.recarry.booking.BookingDtos.BookingRequest;
import com.recarry.booking.BookingDtos.BookingResponse;

import jakarta.validation.Valid;

/** 로그인한 사용자 본인의 예약만 다룬다. */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

	private final BookingService service;

	public BookingController(BookingService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public BookingResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BookingRequest req) {
		return service.create(CurrentUser.id(jwt), req);
	}

	@GetMapping
	public List<BookingResponse> mine(@AuthenticationPrincipal Jwt jwt) {
		return service.listMine(CurrentUser.id(jwt));
	}

	@GetMapping("/{bookingNumber}")
	public BookingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String bookingNumber) {
		return service.getMine(CurrentUser.id(jwt), bookingNumber);
	}

	@PostMapping("/{bookingNumber}/cancel")
	public BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String bookingNumber) {
		return service.cancelMine(CurrentUser.id(jwt), bookingNumber);
	}
}
