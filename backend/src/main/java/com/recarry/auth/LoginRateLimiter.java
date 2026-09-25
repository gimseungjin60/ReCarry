package com.recarry.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.recarry.common.ApiException;
import com.recarry.common.AppProperties;

/**
 * 로그인 실패 제한 — 같은 이메일, 같은 IP 로 창(window) 안에서 너무 많이 틀리면 잠시 막는다.
 * 인스턴스 메모리에만 있다 (재시작하면 초기화, 서버 여러 대면 각자 센다).
 * ponytail: 단일 인스턴스 전제. 서버를 여러 대로 늘리면 DB/Redis 기반 카운터로 옮긴다.
 */
@Component
public class LoginRateLimiter {

	private record Window(Instant start, int failures) {}

	private final Map<String, Window> counters = new ConcurrentHashMap<>();
	private final Clock clock;
	private final AppProperties.LoginLimit cfg;

	public LoginRateLimiter(Clock clock, AppProperties props) {
		this.clock = clock;
		this.cfg = props.loginLimit();
	}

	/** 이미 한도를 넘었으면 비밀번호를 확인하기 전에 429 로 막는다. */
	public void checkAllowed(String email, String ip) {
		if (failures("e:" + email) >= cfg.maxFailuresPerEmail() || failures("i:" + ip) >= cfg.maxFailuresPerIp()) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
				"로그인 시도가 너무 많습니다. " + cfg.windowMinutes() + "분 뒤에 다시 시도해주세요.");
		}
	}

	public void recordFailure(String email, String ip) {
		bump("e:" + email);
		bump("i:" + ip);
	}

	/** 성공하면 그 이메일의 실패 기록만 지운다 (같은 IP 의 다른 계정 시도는 계속 센다). */
	public void recordSuccess(String email) {
		counters.remove("e:" + email);
	}

	private int failures(String key) {
		Window w = counters.get(key);
		return w == null || expired(w) ? 0 : w.failures();
	}

	private void bump(String key) {
		counters.compute(key, (k, w) -> w == null || expired(w) ? new Window(clock.instant(), 1) : new Window(w.start(), w.failures() + 1));
		// 오래된 기록이 쌓이지 않게 가끔 정리한다
		if (counters.size() > 10_000) counters.values().removeIf(this::expired);
	}

	private boolean expired(Window w) {
		return w.start().plus(Duration.ofMinutes(cfg.windowMinutes())).isBefore(clock.instant());
	}
}
