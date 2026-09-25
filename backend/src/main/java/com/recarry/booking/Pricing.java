package com.recarry.booking;

/**
 * 요금 계산 — 서버가 유일한 기준이다. (프론트 금액은 표시용)
 * 기본 요금이 2박 3일을 포함하고, 3박째부터 1박당 추가 요금이 붙는다. 금액 자체는 sample 값.
 */
public final class Pricing {

	public static final int INCLUDED_NIGHTS = 2;

	private Pricing() {}

	public record Quote(int base, int extra, int total) {}

	public static Quote quote(int price, int extraNightPrice, int nights) {
		int extra = Math.max(0, nights - INCLUDED_NIGHTS) * extraNightPrice;
		return new Quote(price, extra, price + extra);
	}
}
