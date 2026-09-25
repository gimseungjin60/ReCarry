package com.recarry.carrier;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "model_id")
	private CarrierModel model;

	@Column(name = "carrier_code")
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

	@OneToMany(mappedBy = "carrier")
	@OrderBy("eventDate ASC, id ASC")
	private List<CarrierEvent> events = new ArrayList<>();

	@UpdateTimestamp
	@Column(name = "updated_at")
	private Instant updatedAt;

	protected Carrier() {}

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

	public void changeStatus(CarrierStatus status) {
		this.status = status;
	}
}
