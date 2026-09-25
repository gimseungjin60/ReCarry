package com.recarry.carrier;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.recarry.carrier.CarrierDtos.AvailabilityResponse;
import com.recarry.carrier.CarrierDtos.CarrierModelResponse;
import com.recarry.carrier.CarrierDtos.CarrierResponse;

/** 공개 카탈로그 API — 로그인 없이 조회할 수 있다. */
@RestController
@RequestMapping("/api/carriers")
public class CarrierController {

	private final CarrierService service;

	public CarrierController(CarrierService service) {
		this.service = service;
	}

	@GetMapping
	public List<CarrierModelResponse> list(@RequestParam(required = false) String size) {
		return service.listModels(size);
	}

	/** availability 는 {code} 보다 먼저 매칭된다 (고정 경로 우선). */
	@GetMapping("/availability")
	public List<AvailabilityResponse> availability(@RequestParam LocalDate start, @RequestParam LocalDate end) {
		return service.availability(start, end);
	}

	@GetMapping("/{code}")
	public CarrierResponse get(@PathVariable String code) {
		return service.getByCode(code);
	}
}
