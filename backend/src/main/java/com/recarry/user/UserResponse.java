package com.recarry.user;

import java.time.Instant;

import com.recarry.common.AppProperties;

/**
 * 비밀번호 해시는 절대 응답에 넣지 않는다.
 * consentRequired: 지금 시행 중인 약관·개인정보 처리방침 버전에 아직 동의하지 않은 일반 회원 — 예약 전에 동의를 받는다.
 */
public record UserResponse(Long id, String email, String name, String phone, Role role, Instant createdAt,
		boolean consentRequired, boolean emailVerified, boolean needsEmailVerification) {

	public static UserResponse of(User u, AppProperties props) {
		return new UserResponse(u.getId(), u.getEmail(), u.getName(), u.getPhone(), u.getRole(), u.getCreatedAt(),
			u.needsConsent(props.legal()), u.getEmailVerifiedAt() != null,
			u.needsEmailVerification(props.mail()));
	}
}
