package com.recarry.auth;

import java.time.Instant;

import com.recarry.user.UserResponse;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

	private AuthDtos() {}

	/** 휴대폰 번호: 숫자·하이픈·공백·+ 만, 9~20자 */
	public static final String PHONE = "^[0-9+\\- ]{9,20}$";

	/** 비밀번호: 영문과 숫자 포함 (길이 8~72 는 따로 — BCrypt 는 72바이트까지만 쓴다) */
	public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d).+$";

	public record SignupRequest(
		@NotBlank(message = "이메일을 입력해주세요.") @Email(message = "이메일 형식이 아닙니다.") @Size(max = 254) String email,
		// BCrypt 는 72바이트까지만 쓴다
		@NotBlank(message = "비밀번호를 입력해주세요.") @Size(min = 8, max = 72, message = "비밀번호는 8~72자입니다.")
		@Pattern(regexp = PASSWORD, message = "비밀번호에 영문과 숫자를 모두 넣어주세요.") String password,
		@NotBlank(message = "이름을 입력해주세요.") @Size(max = 50) String name,
		@NotBlank(message = "휴대폰 번호를 입력해주세요.") @Pattern(regexp = PHONE, message = "휴대폰 번호 형식이 아닙니다.") String phone,
		// 필수 동의 — 보내지 않거나 false 면 거절한다
		@NotNull(message = "이용약관에 동의해주세요.") @AssertTrue(message = "이용약관에 동의해주세요.") Boolean agreeTerms,
		@NotNull(message = "개인정보 수집·이용에 동의해주세요.") @AssertTrue(message = "개인정보 수집·이용에 동의해주세요.") Boolean agreePrivacy) {}

	public record LoginRequest(
		@NotBlank(message = "이메일을 입력해주세요.") @Size(max = 254) String email,
		@NotBlank(message = "비밀번호를 입력해주세요.") @Size(max = 72) String password) {}

	public record ResetRequest(
		@NotBlank(message = "이메일을 입력해주세요.") @Email(message = "이메일 형식이 아닙니다.") @Size(max = 254) String email) {}

	public record ResetConfirm(
		@NotBlank(message = "이메일을 입력해주세요.") @Size(max = 254) String email,
		@NotBlank(message = "인증 코드를 입력해주세요.") @Pattern(regexp = "^[0-9]{6}$", message = "인증 코드는 숫자 6자리입니다.") String code,
		@NotBlank(message = "새 비밀번호를 입력해주세요.") @Size(min = 8, max = 72, message = "비밀번호는 8~72자입니다.")
		@Pattern(regexp = PASSWORD, message = "비밀번호에 영문과 숫자를 모두 넣어주세요.") String newPassword) {}

	public record FindEmailRequest(
		@NotBlank(message = "이름을 입력해주세요.") @Size(max = 50) String name,
		@NotBlank(message = "휴대폰 번호를 입력해주세요.") @Pattern(regexp = PHONE, message = "휴대폰 번호 형식이 아닙니다.") String phone) {}

	/** 가입한 이메일 (앞부분만 보이고 나머지는 가린다). 없으면 빈 목록 */
	public record FindEmailResponse(java.util.List<String> emails) {}

	/** 토큰은 본문에 넣지 않는다 — httpOnly 쿠키로만 간다. */
	public record AuthResponse(Instant expiresAt, UserResponse user) {}

	/** 현재 로그인 상태. 로그인하지 않았으면 user 가 null (401 이 아니다) */
	public record SessionResponse(UserResponse user) {}
}
