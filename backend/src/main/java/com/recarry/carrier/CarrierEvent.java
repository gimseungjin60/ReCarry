package com.recarry.carrier;

import java.time.LocalDate;
import java.util.Comparator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Carrier Story 타임라인 한 줄 — 회수 · 수리 · 세척 · 검수 · 여행. */
@Entity
@Table(name = "carrier_events")
public class CarrierEvent {

	public enum Type { COLLECTED, REPAIR, CLEANING, INSPECTION, TRIP }

	/** 타임라인 순서 (Carrier.events 의 @OrderBy 와 같다). 저장 전 기록은 같은 날짜의 맨 뒤 */
	static final Comparator<CarrierEvent> ORDER = Comparator.comparing(CarrierEvent::getEventDate)
		.thenComparing(CarrierEvent::getId, Comparator.nullsLast(Comparator.naturalOrder()));

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
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

	CarrierEvent(Carrier carrier, LocalDate eventDate, Type type, String title, String detail) {
		this.carrier = carrier;
		this.eventDate = eventDate;
		this.type = type;
		this.title = title;
		this.detail = detail;
	}

	public Long getId() { return id; }
	public LocalDate getEventDate() { return eventDate; }
	public Type getType() { return type; }
	public String getTitle() { return title; }
	public String getDetail() { return detail; }
}
