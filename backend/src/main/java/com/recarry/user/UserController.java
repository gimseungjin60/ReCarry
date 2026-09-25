package com.recarry.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.recarry.auth.CurrentUser;
import com.recarry.common.ApiException;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserRepository users;

	public UserController(UserRepository users) {
		this.users = users;
	}

	@GetMapping("/me")
	@Transactional(readOnly = true)
	public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
		// 토큰은 유효하지만 계정이 지워진 경우
		return users.findById(CurrentUser.id(jwt))
			.map(UserResponse::of)
			.orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
	}
}
