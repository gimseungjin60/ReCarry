package com.recarry.auth;

import java.time.Clock;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recarry.auth.AuthDtos.LoginRequest;
import com.recarry.auth.AuthDtos.SignupRequest;
import com.recarry.common.ApiException;
import com.recarry.common.AppProperties;
import com.recarry.user.Role;
import com.recarry.user.User;
import com.recarry.user.UserRepository;
import com.recarry.user.UserResponse;

@Service
public class AuthService {

	private final UserRepository users;
	private final PasswordEncoder encoder;
	private final TokenService tokens;
	private final LoginRateLimiter limiter;
	private final AppProperties props;
	private final Clock clock;
	private final EmailVerification verification;
	/** 없는 이메일로 로그인할 때도 해시 비교 시간을 들여, 응답 시간으로 가입 여부가 드러나지 않게 한다. */
	private final String dummyHash;

	public AuthService(UserRepository users, PasswordEncoder encoder, TokenService tokens, LoginRateLimiter limiter,
			AppProperties props, Clock clock, EmailVerification verification) {
		this.verification = verification;
		this.users = users;
		this.encoder = encoder;
		this.tokens = tokens;
		this.limiter = limiter;
		this.props = props;
		this.clock = clock;
		this.dummyHash = encoder.encode("timing-equalizer-not-a-real-password");
	}

	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	/** 로그인 결과: 응답에 줄 사용자 정보 + 쿠키에 담을 토큰 */
	public record Session(UserResponse user, TokenService.Issued issued) {}

	@Transactional
	public Session signup(SignupRequest req) {
		String email = normalizeEmail(req.email());
		// 탈퇴 계정의 비식별 이메일(User.withdraw)과 겹치지 않게 한다
		if (email.endsWith("@" + User.WITHDRAWN_DOMAIN)) {
			throw ApiException.badRequest("EMAIL_NOT_ALLOWED", "사용할 수 없는 이메일입니다.");
		}
		if (users.existsByEmail(email)) {
			throw ApiException.conflict("EMAIL_TAKEN", "이미 가입된 이메일입니다.");
		}
		User user = new User(email, encoder.encode(req.password()), req.name().trim(), req.phone().trim(), Role.USER);
		// 검증(@AssertTrue)을 통과했다 = 두 필수 항목에 동의했다. 동의한 문서 버전과 시각을 남긴다
		user.agree(props.legal().termsVersion(), props.legal().privacyVersion(), clock.instant());
		try {
			users.saveAndFlush(user);
		} catch (DataIntegrityViolationException e) {
			// 동시에 같은 이메일로 가입한 경우 (lower(email) unique 인덱스)
			throw ApiException.conflict("EMAIL_TAKEN", "이미 가입된 이메일입니다.");
		}
		verification.send(user);   // 메일을 보낼 수 없는 환경이면 보내지 않는다
		return respond(user);
	}

	@Transactional   // 2단계 인증: 사용한 OTP 단계를 기록한다
	public Session login(LoginRequest req) {
		String email = normalizeEmail(req.email());
		limiter.checkAllowed(email);
		User user = users.findByEmail(email).orElse(null);
		String hash = user != null ? user.getPasswordHash() : dummyHash;
		if (!encoder.matches(req.password(), hash) || user == null) {
			limiter.recordFailure(email);
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
		}
		if (user.isTotpEnabled()) {
			if (req.otp() == null || req.otp().isBlank()) {
				throw new ApiException(HttpStatus.UNAUTHORIZED, "OTP_REQUIRED", "OTP 앱의 6자리 인증 코드를 입력해주세요.");
			}
			long step = Totp.verify(user.getTotpSecret(), req.otp().trim(), Totp.step(clock.instant().getEpochSecond()), user.getTotpLastStep());
			if (step < 0) {
				limiter.recordFailure(email);
				throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_OTP", "인증 코드가 올바르지 않습니다.");
			}
			user.useTotp(step);
		}
		limiter.recordSuccess(email);
		return respond(user);
	}

	private Session respond(User user) {
		return new Session(UserResponse.of(user, props), tokens.issue(user));
	}
}
