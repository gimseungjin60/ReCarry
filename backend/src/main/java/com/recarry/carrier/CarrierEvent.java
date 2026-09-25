package com.recarry.carrier;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Carrier Story 타임라인 한 줄 — 회수 · 수리 · 세척 · 검수 · 여행. */
@Entity
@Table(name = "carrier_events")
public class CarrierEvent {

	public enum Type { COLLECTED, REPAIR, CLEANING, INSPECTION, TRIP }

	@Id
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "carrier_id")
	private Carrier carrier;

	@Column(name = "event_date")
	private LocalDate eventDate;

	@Enumerated(EnumType.STRING)
	private Type type;

	private String title;
	private String detail;

	protected CarrierEvent() {}

	public LocalDate getEventDate() { return eventDate; }
	public Type getType() { return type; }
	public String getTitle() { return title; }
	public String getDetail() { return detail; }
}
