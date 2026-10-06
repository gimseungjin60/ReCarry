package com.recarry.carrier;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** 캐리어 한 대의 검수 항목 결과 한 줄 (carrier_inspection_checks). */
@Embeddable
public class InspectionCheck {

	/** 화면 체크리스트와 같은 순서 */
	public enum Item { CLEANING, EXTERIOR, WHEELS, HANDLE, ZIPPER }

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Item item;

	@Column(nullable = false)
	private boolean passed;

	@Column(nullable = false)
	private String note;

	protected InspectionCheck() {}

	public InspectionCheck(Item item, boolean passed, String note) {
		this.item = item;
		this.passed = passed;
		this.note = note;
	}

	public Item getItem() { return item; }
	public boolean isPassed() { return passed; }
	public String getNote() { return note; }
}
