package com.recarry.carrier;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** 캐리어 한 대. status 는 운영 상태만 담는다 (예약 여부는 bookings 로 계산). */
@Entity
@Table(name = "carriers")
public class Carrier {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "model_id", updatable = false)
	private CarrierModel model;

	/** Carrier ID — 예약 기록이 이 값을 보여주므로 등록 뒤에는 바꾸지 않는다 */
	@Column(name = "carrier_code", updatable = false)
	private String code;

	private String grade;

	@Enumerated(EnumType.STRING)
	private CarrierStatus status;

	@Column(name = "collected_from")
	private String collectedFrom;

	@Column(name = "repair_summary")
	private String repairSummary;

	@Column(name = "inspected_at")
	private LocalDate inspectedAt;

	private boolean featured;

	@OneToMany(mappedBy = "carrier", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("eventDate ASC, id ASC")
	private List<CarrierEvent> events = new ArrayList<>();

	/** 현재 검수 결과 (항목별). 검수일은 inspectedAt */
	@ElementCollection
	@CollectionTable(name = "carrier_inspection_checks", joinColumns = @JoinColumn(name = "carrier_id"))
	private List<InspectionCheck> inspection = new ArrayList<>();

	@UpdateTimestamp
	@Column(name = "updated_at")
	private Instant updatedAt;

	protected Carrier() {}

	/** 새 캐리어는 세척·검수 전 상태로 들어온다 (DB 기본값과 같다). */
	public Carrier(CarrierModel model, String code, String grade, String collectedFrom, String repairSummary) {
		this.model = model;
		this.code = code;
		this.grade = grade;
		this.status = CarrierStatus.INSPECTION;
		this.collectedFrom = collectedFrom;
		this.repairSummary = repairSummary;
	}

	public Long getId() { return id; }
	public CarrierModel getModel() { return model; }
	public String getCode() { return code; }
	public String getGrade() { return grade; }
	public CarrierStatus getStatus() { return status; }
	public String getCollectedFrom() { return collectedFrom; }
	public String getRepairSummary() { return repairSummary; }
	public LocalDate getInspectedAt() { return inspectedAt; }
	public boolean isFeatured() { return featured; }
	public List<CarrierEvent> getEvents() { return events; }
	public List<InspectionCheck> getInspection() { return inspection; }

	public void changeStatus(CarrierStatus status) {
		this.status = status;
	}

	public void edit(String grade, String collectedFrom, String repairSummary) {
		this.grade = grade;
		this.collectedFrom = collectedFrom;
		this.repairSummary = repairSummary;
	}

	public void setFeatured(boolean featured) {
		this.featured = featured;
	}

	/** 검수 결과를 통째로 바꾼다 — 한 번의 검수가 모든 항목을 본다. */
	public void recordInspection(LocalDate inspectedAt, List<InspectionCheck> checks) {
		this.inspectedAt = inspectedAt;
		this.inspection.clear();
		this.inspection.addAll(checks);
	}

	public CarrierEvent addEvent(LocalDate date, CarrierEvent.Type type, String title, String detail) {
		CarrierEvent e = new CarrierEvent(this, date, type, title, detail);
		events.add(e);
		events.sort(CarrierEvent.ORDER);
		return e;
	}

	/** @return 이 캐리어의 기록이었으면 true */
	public boolean removeEvent(Long eventId) {
		return events.removeIf(e -> e.getId() != null && e.getId().equals(eventId));
	}
}
