package com.recarry.common;

import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 의 recarry.* — 비밀 값은 모두 환경변수에서 들어온다. */
@ConfigurationProperties("recarry")
public record AppProperties(Jwt jwt, Cors cors, Cookie cookie, LoginLimit loginLimit, Admin admin, Booking booking) {

	public record Jwt(String secret, long ttlMinutes) {}

	public record Cors(List<String> allowedOrigins) {}

	/** JWT 를 담는 httpOnly 쿠키. sameSite 는 Lax · Strict · None (None 이면 secure 필수) */
	public record Cookie(String name, boolean secure, String sameSite) {}

	/** 로그인 실패 제한 — 창(window) 안에서 이메일별 · IP별 실패 횟수 */
	public record LoginLimit(int maxFailuresPerEmail, int maxFailuresPerIp, int windowMinutes) {}

	/** 둘 다 비어 있지 않을 때만 첫 관리자를 만든다. */
	public record Admin(String email, String password) {
		public boolean configured() {
			return email != null && !email.isBlank() && password != null && !password.isBlank();
		}
	}

	public record Booking(ZoneId zone, int maxNights) {}
}
