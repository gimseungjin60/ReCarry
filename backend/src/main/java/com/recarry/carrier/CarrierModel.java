package com.recarry.carrier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 사이즈 단위 상품 정보와 요금 (읽기 전용 — 카탈로그는 migration 으로 관리한다). */
@Entity
@Table(name = "carrier_models")
public class CarrierModel {

	@Id
	private Long id;

	private String size;
	private String name;
	private String inch;
	private String capacity;
	private String usage;
	private String dims;
	private String weight;

	/** 2박 3일 기본 요금 (원) */
	private int price;

	/** 3박째부터 1박당 추가 요금 (원) */
	@Column(name = "extra_night_price")
	private int extraNightPrice;

	/** 줄바꿈은 '\n' */
	private String headline;
	private String description;

	protected CarrierModel() {}

	public Long getId() { return id; }
	public String getSize() { return size; }
	public String getName() { return name; }
	public String getInch() { return inch; }
	public String getCapacity() { return capacity; }
	public String getUsage() { return usage; }
	public String getDims() { return dims; }
	public String getWeight() { return weight; }
	public int getPrice() { return price; }
	public int getExtraNightPrice() { return extraNightPrice; }
	public String getHeadline() { return headline; }
	public String getDescription() { return description; }
}
