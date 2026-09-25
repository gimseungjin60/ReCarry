package com.recarry.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/** 토큰의 sub 가 users.id 다. */
public final class CurrentUser {

	private CurrentUser() {}

	public static Long id(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}
}
