package com.recarry.common;

import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 의 recarry.* — 비밀 값은 모두 환경변수에서 들어온다. */
@ConfigurationProperties("recarry")
public record AppProperties(Jwt jwt, Cors cors, Cookie cookie, LoginLimit loginLimit, Admin admin, Booking booking,
		Legal legal, Mail mail, Payment payment, Kakao kakao, String appUrl) {

	public record Jwt(String secret, long ttlMinutes) {}

	public record Cors(List<String> allowedOrigins) {}

	/** JWT 를 담는 httpOnly 쿠키. sameSite 는 Lax · Strict · None (None 이면 secure 필수) */
	public record Cookie(String name, boolean secure, String sameSite) {}

	/** 로그인 실패 제한 — 창(window) 안에서 이메일별 실패 횟수 (IP 는 쓰지 않는다 — LoginRateLimiter) */
	public record LoginLimit(int maxFailuresPerEmail, int windowMinutes) {}

	/** 둘 다 비어 있지 않을 때만 첫 관리자를 만든다. */
	public record Admin(String email, String password) {
		public boolean configured() {
			return email != null && !email.isBlank() && password != null && !password.isBlank();
		}
	}

	/**
	 * depositPercent: 보증금 비율(대여 요금 대비 %) · requestExpiryDays: 예약 요청 자동 만료(일)
	 * requestExpiryFrom: 이 날짜(서비스 지역 기준) 이후 접수된 요청에만 만료를 적용한다
	 */
	public record Booking(ZoneId zone, int maxNights, int depositPercent, int requestExpiryDays,
			java.time.LocalDate requestExpiryFrom) {
		/** 자동 만료가 적용되기 시작하는 시각 (서비스 지역 자정) */
		public java.time.Instant expiryAppliesFrom() {
			return requestExpiryFrom.atStartOfDay(zone).toInstant();
		}
	}

	/**
	 * 거래 메일 발송 (Brevo HTTPS API). apiKey 가 비어 있으면 보내지 않는다.
	 * requireVerifiedEmail: 메일을 보낼 수 있을 때만 "이메일 인증 후 예약"을 강제한다 (못 보내면 인증할 방법이 없다)
	 */
	public record Mail(String apiKey, String from, String fromName, boolean logOnly, boolean requireVerifiedEmail) {
		public boolean configured() {
			return apiKey != null && !apiKey.isBlank() && from != null && !from.isBlank();
		}

		/** 실제로 보내거나(운영) 로그로 대신할 수 있다(로컬) */
		public boolean canSend() {
			return configured() || logOnly;
		}
	}

	/** 무통장 입금 계좌. 비어 있으면 임시(sample) 계좌를 보여주고 "입금하지 마세요"라고 알린다 */
	public record Payment(String bankName, String bankAccount, String bankHolder) {
		public boolean sample() {
			return bankAccount == null || bankAccount.isBlank();
		}

		public String bank() { return sample() ? "OO은행" : bankName; }
		public String account() { return sample() ? "000-000000-00-000" : bankAccount; }
		public String holder() { return sample() ? "RECARRY (임시 계좌)" : bankHolder; }
	}

	/** 카카오 로그인 — REST API 키와 Client Secret 이 모두 있어야 켜진다. Redirect URI = appUrl + /api/auth/kakao/callback */
	public record Kakao(String clientId, String clientSecret, String authBase, String apiBase) {
		public boolean configured() {
			return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
		}
	}

	/** 가입 시 동의받는 문서 버전 — 문서가 바뀌면 함께 올린다 */
	public record Legal(String termsVersion, String privacyVersion) {}
}
