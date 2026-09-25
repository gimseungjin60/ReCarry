package com.recarry.auth;

import java.time.Instant;

import com.recarry.user.UserResponse;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

	private AuthDtos() {}

	/** 휴대폰 번호: 숫자·하이픈·공백·+ 만, 9~20자 */
	public static final String PHONE = "^[0-9+\\- ]{9,20}$";

	public record SignupRequest(
		@NotBlank(message = "이메일을 입력해주세요.") @Email(message = "이메일 형식이 아닙니다.") @Size(max = 254) String email,
		// BCrypt 는 72바이트까지만 쓴다
		@NotBlank(message = "비밀번호를 입력해주세요.") @Size(min = 8, max = 72, message = "비밀번호는 8~72자입니다.")
		@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "비밀번호에 영문과 숫자를 모두 넣어주세요.") String password,
		@NotBlank(message = "이름을 입력해주세요.") @Size(max = 50) String name,
		@NotBlank(message = "휴대폰 번호를 입력해주세요.") @Pattern(regexp = PHONE, message = "휴대폰 번호 형식이 아닙니다.") String phone) {}

	public record LoginRequest(
		@NotBlank(message = "이메일을 입력해주세요.") @Size(max = 254) String email,
		@NotBlank(message = "비밀번호를 입력해주세요.") @Size(max = 72) String password) {}

	/** 토큰은 본문에 넣지 않는다 — httpOnly 쿠키로만 간다. */
	public record AuthResponse(Instant expiresAt, UserResponse user) {}

	/** 현재 로그인 상태. 로그인하지 않았으면 user 가 null (401 이 아니다) */
	public record SessionResponse(UserResponse user) {}
}
