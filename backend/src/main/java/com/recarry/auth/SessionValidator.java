package com.recarry.auth;

import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import com.recarry.user.UserRepository;

/**
 * 서명·만료가 맞아도 다음이면 토큰을 거절한다:
 * 계정이 없거나 탈퇴함 · 비밀번호 변경/탈퇴 이전에 발급됨 · 토큰의 권한이 지금 권한과 다름(강등 즉시 반영).
 * ponytail: 인증 요청마다 users 한 행을 읽는다 — 트래픽이 커지면 짧은 캐시를 둔다.
 */
class SessionValidator implements OAuth2TokenValidator<Jwt> {

	private static final OAuth2TokenValidatorResult REVOKED =
		OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "session revoked", null));

	private final UserRepository users;

	SessionValidator(UserRepository users) {
		this.users = users;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		Long id;
		try {
			id = Long.valueOf(jwt.getSubject());
		} catch (NumberFormatException e) {
			return REVOKED;
		}
		return users.findSessionState(id)
			.filter(s -> s.getDeletedAt() == null)
			.filter(s -> !issuedBefore(jwt.getIssuedAt(), s.getSessionsValidAfter()))
			.filter(s -> List.of(s.getRole().name()).equals(jwt.getClaimAsStringList("roles")))
			.map(s -> OAuth2TokenValidatorResult.success())
			.orElse(REVOKED);
	}

	/** 초 단위로 비교한다 — 토큰 iat 는 초 단위로 잘린다 */
	private static boolean issuedBefore(Instant issuedAt, Instant validAfter) {
		if (validAfter == null) return false;
		return issuedAt == null || issuedAt.getEpochSecond() < validAfter.getEpochSecond();
	}
}
