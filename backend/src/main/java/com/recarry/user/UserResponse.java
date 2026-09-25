package com.recarry.user;

import java.time.Instant;

/** 비밀번호 해시는 절대 응답에 넣지 않는다. */
public record UserResponse(Long id, String email, String name, String phone, Role role, Instant createdAt) {

	public static UserResponse of(User u) {
		return new UserResponse(u.getId(), u.getEmail(), u.getName(), u.getPhone(), u.getRole(), u.getCreatedAt());
	}
}
