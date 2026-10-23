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
 * 시도 횟수 제한 — 같은 키(이메일 · 이름+번호 · 전체)로 창(window) 안에서 너무 많이 시도하면 잠시 막는다.
 * IP 는 쓰지 않는다: Vercel rewrite 뒤에서는 모든 사용자가 몇 개의 Vercel IP 로 보여서,
 * IP 한도는 서비스 전체를 잠그는 수단이 된다 (공격자가 일부러 실패를 쌓아 모두의 로그인을 막을 수 있다).
 * 인스턴스 메모리에만 있다 (재시작하면 초기화).
 * ponytail: 단일 인스턴스 전제. 서버를 여러 대로 늘리면 DB/Redis 기반 카운터로 옮긴다.
 */
@Component
public class LoginRateLimiter {

	/** 서로 다른 키가 이만큼 쌓이면(창 안의 대량 시도) 만료된 것을 지우고, 그래도 넘치면 비운다 — 메모리 보호 */
	private static final int MAX_KEYS = 50_000;

	private record Window(Instant start, int count) {}

	private final Map<String, Window> counters = new ConcurrentHashMap<>();
	private final Clock clock;
	private final AppProperties.LoginLimit cfg;

	public LoginRateLimiter(Clock clock, AppProperties props) {
		this.clock = clock;
		this.cfg = props.loginLimit();
	}

	/** 이미 한도를 넘었으면 비밀번호를 확인하기 전에 429 로 막는다. */
	public void checkAllowed(String email) {
		if (count("e:" + email) >= cfg.maxFailuresPerEmail()) throw tooMany("로그인 시도가 너무 많습니다.");
	}

	public void recordFailure(String email) {
		bump("e:" + email);
	}

	/** 성공하면 그 이메일의 실패 기록을 지운다. */
	public void recordSuccess(String email) {
		counters.remove("e:" + email);
	}

	/**
	 * 시도할 때마다 센다 — 창 안에서 max 번을 넘으면 429. 확인과 증가를 한 번에(원자적으로) 한다.
	 * key 는 용도별 접두사를 붙여 로그인 실패 카운터와 섞이지 않게 한다.
	 */
	public void consume(String key, int max) {
		Window w = bump(key);
		if (w.count() > max) throw tooMany("요청이 너무 많습니다.");
	}

	private ApiException tooMany(String what) {
		return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
			what + " " + cfg.windowMinutes() + "분 뒤에 다시 시도해주세요.");
	}

	private int count(String key) {
		Window w = counters.get(key);
		return w == null || expired(w) ? 0 : w.count();
	}

	private Window bump(String key) {
		if (counters.size() > MAX_KEYS) {
			counters.values().removeIf(this::expired);
			if (counters.size() > MAX_KEYS) counters.clear();
		}
		return counters.compute(key, (k, w) -> w == null || expired(w) ? new Window(clock.instant(), 1) : new Window(w.start(), w.count() + 1));
	}

	private boolean expired(Window w) {
		return w.start().plus(Duration.ofMinutes(cfg.windowMinutes())).isBefore(clock.instant());
	}
}
