package com.recarry.auth;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recarry.auth.AuthDtos.LoginRequest;
import com.recarry.auth.AuthDtos.SignupRequest;
import com.recarry.common.ApiException;
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
	/** 없는 이메일로 로그인할 때도 해시 비교 시간을 들여, 응답 시간으로 가입 여부가 드러나지 않게 한다. */
	private final String dummyHash;

	public AuthService(UserRepository users, PasswordEncoder encoder, TokenService tokens, LoginRateLimiter limiter) {
		this.users = users;
		this.encoder = encoder;
		this.tokens = tokens;
		this.limiter = limiter;
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
		if (users.existsByEmail(email)) {
			throw ApiException.conflict("EMAIL_TAKEN", "이미 가입된 이메일입니다.");
		}
		User user = new User(email, encoder.encode(req.password()), req.name().trim(), req.phone().trim(), Role.USER);
		try {
			users.saveAndFlush(user);
		} catch (DataIntegrityViolationException e) {
			// 동시에 같은 이메일로 가입한 경우 (lower(email) unique 인덱스)
			throw ApiException.conflict("EMAIL_TAKEN", "이미 가입된 이메일입니다.");
		}
		return respond(user);
	}

	@Transactional(readOnly = true)
	public Session login(LoginRequest req, String clientIp) {
		String email = normalizeEmail(req.email());
		limiter.checkAllowed(email, clientIp);
		User user = users.findByEmail(email).orElse(null);
		String hash = user != null ? user.getPasswordHash() : dummyHash;
		if (!encoder.matches(req.password(), hash) || user == null) {
			limiter.recordFailure(email, clientIp);
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
		}
		limiter.recordSuccess(email);
		return respond(user);
	}

	private Session respond(User user) {
		return new Session(UserResponse.of(user), tokens.issue(user));
	}
}
