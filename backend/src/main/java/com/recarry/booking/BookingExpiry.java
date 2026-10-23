package com.recarry.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.recarry.common.AppProperties;

/**
 * 확정되지 않은 예약 요청(REQUESTED)은 접수 후 recarry.booking.request-expiry-days 가 지나면 자동 취소한다.
 * 기동 직후 한 번 + 5분마다. (Render free 는 잠들었다 깨면 새로 기동하므로 기동 시 정리가 곧 따라잡기다.)
 * ponytail: 최대 5분 늦게 풀린다 — 분 단위 정확도가 필요해지면 조회 시점 정리로 바꾼다.
 */
@Component
public class BookingExpiry {

	private static final Logger log = LoggerFactory.getLogger(BookingExpiry.class);

	private final BookingRepository bookings;
	private final Clock clock;
	private final Duration ttl;
	/** 정책 시행 이후 접수된 요청만 — 3일 규칙을 안내받기 전의 요청은 자동으로 취소하지 않는다 */
	private final Instant appliesFrom;

	public BookingExpiry(BookingRepository bookings, Clock clock, AppProperties props) {
		this.bookings = bookings;
		this.clock = clock;
		this.ttl = Duration.ofDays(props.booking().requestExpiryDays());
		this.appliesFrom = props.booking().expiryAppliesFrom();
	}

	@EventListener(ApplicationReadyEvent.class)
	@Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 5 * 60 * 1000)
	@Transactional
	public void expire() {   // void: 리스너가 값을 돌려주면 Spring 이 그 값을 다시 이벤트로 발행한다
		Instant now = clock.instant();
		int n = bookings.expireRequested(now.minus(ttl), appliesFrom, now);
		if (n > 0) log.info("expired {} unconfirmed booking request(s)", n);
	}
}
