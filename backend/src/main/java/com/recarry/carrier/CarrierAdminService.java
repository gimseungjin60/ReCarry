package com.recarry.carrier;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recarry.carrier.CarrierDtos.CarrierCreateRequest;
import com.recarry.carrier.CarrierDtos.CarrierResponse;
import com.recarry.carrier.CarrierDtos.CarrierUpdateRequest;
import com.recarry.carrier.CarrierDtos.CheckRequest;
import com.recarry.carrier.CarrierDtos.EventRequest;
import com.recarry.carrier.CarrierDtos.InspectionRequest;
import com.recarry.carrier.CarrierDtos.ModelUpdateRequest;
import com.recarry.common.ApiException;
import com.recarry.common.AppProperties;
import com.recarry.common.TimeConfig;

/**
 * 관리자 캐리어 관리 — 등록 · 수정 · 운영 상태 · 검수 결과 · Story 기록 · 사이즈 상품 문구/가격.
 * 운영 상태 전이 규칙이나 세척·검수 버퍼 같은 운영 정책은 여기서 정하지 않는다.
 */
@Service
@Transactional
public class CarrierAdminService {

	private final CarrierRepository carriers;
	private final CarrierModelRepository models;
	private final CarrierService carrierService;
	private final Clock clock;
	private final AppProperties props;

	public CarrierAdminService(CarrierRepository carriers, CarrierModelRepository models, CarrierService carrierService,
			Clock clock, AppProperties props) {
		this.carriers = carriers;
		this.models = models;
		this.carrierService = carrierService;
		this.clock = clock;
		this.props = props;
	}

	@Transactional(readOnly = true)
	public List<CarrierResponse> list() {
		var today = carrierService.statusToday();
		return carriers.findAllWithModel().stream().map(c -> CarrierService.toResponse(c, today)).toList();
	}

	public CarrierResponse create(CarrierCreateRequest req) {
		CarrierModel model = models.findBySize(req.size())
			.orElseThrow(() -> ApiException.badRequest("MODEL_NOT_FOUND", "등록된 사이즈가 아닙니다."));
		if (carriers.existsByCode(req.code())) {
			throw ApiException.conflict("CARRIER_CODE_TAKEN", "이미 등록된 Carrier ID 입니다.");
		}
		Carrier c = new Carrier(model, req.code(), req.grade(), req.collectedFrom().trim(), blankIfNull(req.repairSummary()));
		if (Boolean.TRUE.equals(req.featured())) makeFeatured(c);
		carriers.saveAndFlush(c);
		return respond(c);
	}

	public CarrierResponse update(Long id, CarrierUpdateRequest req) {
		Carrier c = find(id);
		c.edit(req.grade(), req.collectedFrom().trim(), blankIfNull(req.repairSummary()));
		if (Boolean.TRUE.equals(req.featured()) && !c.isFeatured()) makeFeatured(c);
		else if (Boolean.FALSE.equals(req.featured())) c.setFeatured(false);
		carriers.flush();
		return respond(c);
	}

	/** 운영 상태만 바꿀 수 있다. RESERVED / RENTED 는 예약에서 계산되는 값이다. */
	public CarrierResponse changeStatus(Long id, CarrierStatus status) {
		if (!status.operational()) {
			throw ApiException.badRequest("INVALID_STATUS", "RESERVED / RENTED 는 예약으로 정해지는 상태입니다.");
		}
		Carrier c = find(id);
		c.changeStatus(status);
		carriers.flush();
		return respond(c);
	}

	/** 검수 결과를 기록한다 (이전 결과를 대체). 모든 항목을 한 번씩 담아야 한다. 운영 상태는 바꾸지 않는다 (관리자가 따로 정한다). */
	public CarrierResponse recordInspection(Long id, InspectionRequest req) {
		if (req.inspectedAt().isAfter(TimeConfig.today(clock, props))) {
			throw ApiException.badRequest("INVALID_DATE", "검수일은 오늘 이후일 수 없습니다.");
		}
		Set<InspectionCheck.Item> seen = EnumSet.noneOf(InspectionCheck.Item.class);
		for (CheckRequest r : req.checks()) {
			if (!seen.add(r.item())) throw ApiException.badRequest("INVALID_INSPECTION", "같은 검수 항목이 두 번 있습니다.");
		}
		if (seen.size() != InspectionCheck.Item.values().length) {
			throw ApiException.badRequest("INVALID_INSPECTION", "모든 검수 항목의 결과를 입력해주세요.");
		}
		Carrier c = find(id);
		c.recordInspection(req.inspectedAt(),
			req.checks().stream().map(r -> new InspectionCheck(r.item(), r.passed(), blankIfNull(r.note()))).toList());
		carriers.flush();
		return respond(c);
	}

	public CarrierResponse addEvent(Long id, EventRequest req) {
		Carrier c = find(id);
		c.addEvent(req.date(), req.type(), req.title().trim(), blankIfNull(req.detail()));
		carriers.flush();
		return respond(c);
	}

	public CarrierResponse removeEvent(Long id, Long eventId) {
		Carrier c = find(id);
		if (!c.removeEvent(eventId)) {
			throw ApiException.notFound("EVENT_NOT_FOUND", "기록을 찾을 수 없습니다.");
		}
		carriers.flush();
		return respond(c);
	}

	public void updateModel(String size, ModelUpdateRequest req) {
		CarrierModel m = models.findBySize(size)
			.orElseThrow(() -> ApiException.notFound("MODEL_NOT_FOUND", "등록된 사이즈가 아닙니다."));
		m.edit(req.name().trim(), req.inch().trim(), req.capacity().trim(), req.usage().trim(), req.dims().trim(),
			req.weight().trim(), req.price(), req.extraNightPrice(), req.headline().strip(), req.description().strip());
		models.flush();
	}

	// ----------------------------------------------------------------

	/** 사이즈마다 대표는 하나 — 기존 대표를 먼저 내린다 (partial unique index 순서 때문에 flush). */
	private void makeFeatured(Carrier c) {
		carriers.findByModelAndFeaturedTrue(c.getModel()).forEach(other -> other.setFeatured(false));
		carriers.flush();
		c.setFeatured(true);
	}

	private Carrier find(Long id) {
		return carriers.findById(id)
			.orElseThrow(() -> ApiException.notFound("CARRIER_NOT_FOUND", "캐리어를 찾을 수 없습니다."));
	}

	private CarrierResponse respond(Carrier c) {
		return CarrierService.toResponse(c, carrierService.statusToday());
	}

	private static String blankIfNull(String s) {
		return s == null ? "" : s.trim();
	}
}
