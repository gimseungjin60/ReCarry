package com.recarry.carrier;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recarry.booking.BookingDates;
import com.recarry.booking.Pricing;
import com.recarry.carrier.CarrierDtos.AvailabilityResponse;
import com.recarry.carrier.CarrierDtos.CarrierModelResponse;
import com.recarry.carrier.CarrierDtos.CarrierResponse;
import com.recarry.carrier.CarrierDtos.EventResponse;
import com.recarry.common.ApiException;
import com.recarry.common.AppProperties;
import com.recarry.common.TimeConfig;

@Service
@Transactional(readOnly = true)
public class CarrierService {

	private final CarrierRepository carriers;
	private final BookingDates dates;
	private final Clock clock;
	private final AppProperties props;

	public CarrierService(CarrierRepository carriers, BookingDates dates, Clock clock, AppProperties props) {
		this.carriers = carriers;
		this.dates = dates;
		this.clock = clock;
		this.props = props;
	}

	/** 사이즈별 상품 목록. size 가 있으면 그 사이즈만. */
	public List<CarrierModelResponse> listModels(String size) {
		Map<Long, CarrierStatus> today = statusToday();
		Map<CarrierModel, List<Carrier>> byModel = carriers.findAllWithModel().stream()
			.filter(c -> size == null || c.getModel().getSize().equals(size))
			.collect(Collectors.groupingBy(Carrier::getModel));

		return byModel.entrySet().stream()
			.sorted(Comparator.comparing(e -> e.getKey().getSize()))
			.map(e -> {
				CarrierModel m = e.getKey();
				List<Carrier> units = e.getValue();
				Carrier featured = units.stream().filter(Carrier::isFeatured).findFirst().orElse(units.get(0));
				int available = (int) units.stream().filter(c -> c.getStatus() == CarrierStatus.AVAILABLE).count();
				return new CarrierModelResponse(m.getSize(), m.getName(), m.getInch(), m.getCapacity(), m.getUsage(),
					m.getDims(), m.getWeight(), m.getPrice(), m.getExtraNightPrice(),
					Arrays.asList(m.getHeadline().split("\n")), m.getDescription(), available,
					toResponse(featured, today));
			})
			.toList();
	}

	public CarrierResponse getByCode(String code) {
		Carrier c = carriers.findByCode(code)
			.orElseThrow(() -> ApiException.notFound("CARRIER_NOT_FOUND", "캐리어를 찾을 수 없습니다."));
		return toResponse(c, statusToday());
	}

	/** 기간 안에 사이즈별로 예약 가능한 대수와 금액. */
	public List<AvailabilityResponse> availability(LocalDate start, LocalDate end) {
		int nights = dates.validate(start, end);
		return listModels(null).stream()
			.map(m -> new AvailabilityResponse(m.size(), carriers.findFreeIds(m.size(), start, end).size(), nights,
				Pricing.quote(m.price(), m.extraNightPrice(), nights).total()))
			.toList();
	}

	/** carrier id → 오늘 기준 RESERVED / RENTED (점유가 없으면 목록에 없다) */
	public Map<Long, CarrierStatus> statusToday() {
		LocalDate today = TimeConfig.today(clock, props);
		return carriers.findOccupiedOn(today).stream()
			.collect(Collectors.toMap(CarrierRepository.OccupiedToday::getCarrierId,
				o -> "IN_USE".equals(o.getStatus()) ? CarrierStatus.RENTED : CarrierStatus.RESERVED,
				(a, b) -> a == CarrierStatus.RENTED ? a : b));
	}

	/** 운영 상태가 AVAILABLE 일 때만 예약 상태로 덮어쓴다 (수리 중인 캐리어는 수리 중으로 보인다). */
	public static CarrierStatus displayStatus(Carrier c, Map<Long, CarrierStatus> today) {
		return c.getStatus() == CarrierStatus.AVAILABLE ? today.getOrDefault(c.getId(), c.getStatus()) : c.getStatus();
	}

	public static CarrierResponse toResponse(Carrier c, Map<Long, CarrierStatus> today) {
		return new CarrierResponse(c.getId(), c.getCode(), c.getModel().getSize(), c.getGrade(), displayStatus(c, today),
			c.getCollectedFrom(), c.getRepairSummary(), c.getInspectedAt(),
			c.getEvents().stream().map(EventResponse::of).toList());
	}
}
