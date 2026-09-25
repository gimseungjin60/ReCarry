package com.recarry.booking;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.recarry.common.ApiException;
import com.recarry.common.AppProperties;
import com.recarry.common.TimeConfig;

/** 예약 날짜 규칙. 캐리어는 출발 전날 배송되므로 출발일은 빨라도 내일이다. */
@Component
public class BookingDates {

	private final Clock clock;
	private final AppProperties props;

	public BookingDates(Clock clock, AppProperties props) {
		this.clock = clock;
		this.props = props;
	}

	/** @return 숙박 수 */
	public int validate(LocalDate start, LocalDate end) {
		if (start == null || end == null) {
			throw ApiException.badRequest("INVALID_DATES", "출발일과 도착일을 모두 선택해주세요.");
		}
		LocalDate today = TimeConfig.today(clock, props);
		if (!start.isAfter(today)) {
			throw ApiException.badRequest("INVALID_DATES", "출발일은 내일 이후로 선택해주세요.");
		}
		if (!end.isAfter(start)) {
			throw ApiException.badRequest("INVALID_DATES", "도착일은 출발일 이후여야 합니다.");
		}
		long nights = ChronoUnit.DAYS.between(start, end);
		if (nights > props.booking().maxNights()) {
			throw ApiException.badRequest("INVALID_DATES", "한 번에 최대 " + props.booking().maxNights() + "박까지 예약할 수 있습니다.");
		}
		return (int) nights;
	}
}
