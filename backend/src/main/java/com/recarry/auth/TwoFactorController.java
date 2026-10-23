package com.recarry.auth;

import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.recarry.admin.AuditService;
import com.recarry.common.ApiException;
import com.recarry.user.User;
import com.recarry.user.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 관리자 2단계 인증(TOTP) 설정. /api/admin/** 라 ADMIN 만 닿는다.
 * 켜면 로그인에 OTP 코드가 필요하다. 휴대폰을 잃어버리면 운영자가 DB 에서 users.totp_enabled 를 false 로 돌린다.
 */
@RestController
@RequestMapping("/api/admin/2fa")
@Transactional
public class TwoFactorController {

	private final UserRepository users;
	private final AuditService audit;
	private final Clock clock;

	public TwoFactorController(UserRepository users, AuditService audit, Clock clock) {
		this.users = users;
		this.audit = audit;
		this.clock = clock;
	}

	public record Status(boolean enabled) {}

	public record Setup(String secret, String uri) {}

	public record CodeRequest(@NotBlank(message = "인증 코드를 입력해주세요.") @Pattern(regexp = "^[0-9]{6}$", message = "인증 코드는 숫자 6자리입니다.") String code) {}

	@GetMapping
	@Transactional(readOnly = true)
	public Status status(@AuthenticationPrincipal Jwt jwt) {
		return new Status(me(jwt).isTotpEnabled());
	}

	/** 새 secret 발급 — OTP 앱에 등록한 뒤 /enable 로 코드를 확인해야 켜진다 */
	@PostMapping("/setup")
	public Setup setup(@AuthenticationPrincipal Jwt jwt) {
		User u = me(jwt);
		if (u.isTotpEnabled()) throw ApiException.conflict("TOTP_ALREADY_ENABLED", "이미 2단계 인증을 쓰고 있습니다. 끈 뒤 다시 설정해주세요.");
		String secret = Totp.newSecret();
		u.prepareTotp(secret);
		return new Setup(secret, Totp.uri(u.getEmail(), secret));
	}

	@PostMapping("/enable")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void enable(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CodeRequest req) {
		User u = me(jwt);
		if (u.getTotpSecret() == null) throw ApiException.badRequest("TOTP_NOT_PREPARED", "먼저 설정 키를 발급받아주세요.");
		u.enableTotp(check(u, req.code()));
		audit.record(u.getId(), "TOTP_ENABLE", u.getEmail(), "2단계 인증 켬");
	}

	@PostMapping("/disable")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void disable(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CodeRequest req) {
		User u = me(jwt);
		if (!u.isTotpEnabled()) return;
		check(u, req.code());
		u.disableTotp();
		audit.record(u.getId(), "TOTP_DISABLE", u.getEmail(), "2단계 인증 끔");
	}

	private long check(User u, String code) {
		long step = Totp.verify(u.getTotpSecret(), code, Totp.step(clock.instant().getEpochSecond()), u.getTotpLastStep());
		if (step < 0) throw ApiException.badRequest("INVALID_OTP", "인증 코드가 올바르지 않습니다. 앱의 시간이 맞는지 확인해주세요.");
		return step;
	}

	private User me(Jwt jwt) {
		return users.findById(CurrentUser.id(jwt))
			.orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
	}
}
