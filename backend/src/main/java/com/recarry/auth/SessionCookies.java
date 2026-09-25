package com.recarry.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.recarry.common.AppProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JWT 를 httpOnly 쿠키로 주고받는다 — 브라우저 JS 는 토큰을 읽을 수 없다.
 * Secure · SameSite 는 환경변수로 정한다 (로컬 http 에서는 local 프로필이 Secure 를 끈다).
 */
@Component
public class SessionCookies {

	private final AppProperties.Cookie cfg;

	public SessionCookies(AppProperties props) {
		this.cfg = props.cookie();
		if ("None".equalsIgnoreCase(cfg.sameSite()) && !cfg.secure()) {
			throw new IllegalStateException("COOKIE_SAME_SITE=None requires a Secure cookie");
		}
	}

	public void issue(HttpServletResponse res, String token, Instant expiresAt) {
		long seconds = Math.max(0, Duration.between(Instant.now(), expiresAt).getSeconds());
		res.addHeader(HttpHeaders.SET_COOKIE, build(token, seconds));
	}

	public void clear(HttpServletResponse res) {
		res.addHeader(HttpHeaders.SET_COOKIE, build("", 0));
	}

	public Optional<String> read(HttpServletRequest req) {
		Cookie[] cookies = req.getCookies();
		if (cookies == null) return Optional.empty();
		return Arrays.stream(cookies)
			.filter(c -> cfg.name().equals(c.getName()) && !c.getValue().isBlank())
			.map(Cookie::getValue)
			.findFirst();
	}

	private String build(String value, long maxAgeSeconds) {
		return ResponseCookie.from(cfg.name(), value)
			.httpOnly(true)
			.secure(cfg.secure())
			.sameSite(cfg.sameSite())
			.path("/api")
			.maxAge(maxAgeSeconds)
			.build()
			.toString();
	}
}
