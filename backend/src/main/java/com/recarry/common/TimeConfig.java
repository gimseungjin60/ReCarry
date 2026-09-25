package com.recarry.common;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시간은 Clock 으로만 읽는다 (테스트에서 날짜를 고정할 수 있게). */
@Configuration
public class TimeConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	/** 예약 날짜의 "오늘"은 서비스 지역(Asia/Seoul) 기준이다. */
	public static LocalDate today(Clock clock, AppProperties props) {
		return LocalDate.now(clock.withZone(props.booking().zone()));
	}
}
