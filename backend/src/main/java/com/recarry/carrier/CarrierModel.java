package com.recarry.carrier;

import java.time.Instant;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 사이즈 단위 상품 정보와 요금. 사이즈 자체는 바꾸지 않는다 (화면 표현이 사이즈에 묶여 있다). */
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

	@UpdateTimestamp
	@Column(name = "updated_at")
	private Instant updatedAt;

	protected CarrierModel() {}

	/** 가격 · 문구 수정. 기존 예약은 금액 스냅샷이라 영향이 없다. */
	public void edit(String name, String inch, String capacity, String usage, String dims, String weight, int price,
			int extraNightPrice, String headline, String description) {
		this.name = name;
		this.inch = inch;
		this.capacity = capacity;
		this.usage = usage;
		this.dims = dims;
		this.weight = weight;
		this.price = price;
		this.extraNightPrice = extraNightPrice;
		this.headline = headline;
		this.description = description;
	}

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
