package com.recarry.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.recarry.auth.AuthDtos;
import com.recarry.auth.CurrentUser;
import com.recarry.auth.EmailVerification;
import com.recarry.auth.SessionCookies;
import com.recarry.auth.TokenService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 로그인한 사용자 본인의 계정. 이메일은 바꾸지 않는다 (로그인 ID). */
@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService users;
	private final TokenService tokens;
	private final SessionCookies cookies;
	private final EmailVerification verification;

	public UserController(UserService users, TokenService tokens, SessionCookies cookies, EmailVerification verification) {
		this.verification = verification;
		this.users = users;
		this.tokens = tokens;
		this.cookies = cookies;
	}

	public record ProfileRequest(
		@NotBlank(message = "이름을 입력해주세요.") @Size(max = 50) String name,
		@NotBlank(message = "휴대폰 번호를 입력해주세요.") @Pattern(regexp = AuthDtos.PHONE, message = "휴대폰 번호 형식이 아닙니다.") String phone) {}

	public record PasswordRequest(
		@NotBlank(message = "현재 비밀번호를 입력해주세요.") @Size(max = 72) String currentPassword,
		@NotBlank(message = "새 비밀번호를 입력해주세요.") @Size(min = 8, max = 72, message = "비밀번호는 8~72자입니다.")
		@Pattern(regexp = AuthDtos.PASSWORD, message = "비밀번호에 영문과 숫자를 모두 넣어주세요.") String newPassword) {}

	/** 시행 중인 문서에 다시 동의 — 둘 다 true 여야 한다 (가입 동의와 같은 규칙) */
	public record ConsentRequest(
		@NotNull(message = "이용약관에 동의해주세요.") @AssertTrue(message = "이용약관에 동의해주세요.") Boolean agreeTerms,
		@NotNull(message = "개인정보 수집·이용에 동의해주세요.") @AssertTrue(message = "개인정보 수집·이용에 동의해주세요.") Boolean agreePrivacy) {}

	public record WithdrawRequest(@NotBlank(message = "비밀번호를 입력해주세요.") @Size(max = 72) String password) {}

	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
		return users.respond(users.get(CurrentUser.id(jwt)));
	}

	/** 인증 메일 다시 보내기 (15분에 3번) */
	@PostMapping("/me/verify-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resendVerification(@AuthenticationPrincipal Jwt jwt) {
		verification.resend(CurrentUser.id(jwt));
	}

	@PostMapping("/me/consent")
	public UserResponse consent(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConsentRequest req) {
		return users.respond(users.agreeToCurrent(CurrentUser.id(jwt)));
	}

	@PatchMapping("/me")
	public UserResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest req) {
		return users.respond(users.updateProfile(CurrentUser.id(jwt), req.name(), req.phone()));
	}

	/** 다른 기기의 로그인은 끊고, 이 브라우저에는 새 세션 쿠키를 준다. */
	@PostMapping("/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordRequest req,
			HttpServletResponse res) {
		User u = users.changePassword(CurrentUser.id(jwt), req.currentPassword(), req.newPassword());
		TokenService.Issued issued = tokens.issue(u);
		cookies.issue(res, issued.token(), issued.expiresAt());
	}

	@PostMapping("/me/withdraw")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void withdraw(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WithdrawRequest req,
			HttpServletResponse res) {
		users.withdraw(CurrentUser.id(jwt), req.password());
		cookies.clear(res);
	}
}
