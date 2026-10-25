package com.recarry.auth;

import java.time.Clock;
import java.time.Duration;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recarry.common.ApiException;
import com.recarry.common.AppProperties;
import com.recarry.common.Mailer;
import com.recarry.user.User;
import com.recarry.user.UserRepository;

/**
 * 가입 이메일 인증 — 서명한 링크(24시간)를 메일로 보낸다. DB 테이블 없이 HMAC(JWT 서명 키)으로 위조를 막는다.
 * 토큰에 이메일을 함께 서명하므로, 탈퇴하면(이메일이 바뀌면) 예전 링크는 쓸 수 없다.
 */
@Service
public class EmailVerification {

	private static final Duration TTL = Duration.ofHours(24);

	private final UserRepository users;
	private final SecretKey key;
	private final Mailer mailer;
	private final AppProperties props;
	private final LoginRateLimiter limiter;
	private final Clock clock;

	public EmailVerification(UserRepository users, SecretKey jwtKey, Mailer mailer, AppProperties props,
			LoginRateLimiter limiter, Clock clock) {
		this.users = users;
		this.key = jwtKey;
		this.mailer = mailer;
		this.props = props;
		this.limiter = limiter;
		this.clock = clock;
	}

	/** 인증 메일을 보낸다 (이미 인증했거나 메일을 보낼 수 없으면 아무것도 하지 않는다) */
	public void send(User u) {
		if (u.getEmailVerifiedAt() != null || !props.mail().canSend()) return;
		long exp = clock.instant().plus(TTL).getEpochSecond();
		mailer.sendAfterCommit(u.getEmail(), "[RECARRY] 이메일 인증을 완료해주세요",
			"RECARRY 가입을 환영합니다. 아래 링크를 눌러 이메일 인증을 마치면 예약할 수 있습니다 (24시간 유효).\n\n"
				+ props.appUrl() + "/verify-email?token=" + token(u.getId(), u.getEmail(), exp)
				+ "\n\n본인이 가입하지 않았다면 이 메일을 무시하세요.");
	}

	/** 인증 토큰: userId.만료시각.서명 */
	public String token(long userId, String email, long exp) {
		return userId + "." + exp + "." + sign(userId, email, exp);
	}

	/** 로그인한 회원이 다시 요청 — 15분에 3번까지 */
	@Transactional(readOnly = true)
	public void resend(Long userId) {
		limiter.consume("verify-u:" + userId, 3);
		users.findById(userId).filter(u -> u.getDeletedAt() == null).ifPresent(this::send);
	}

	@Transactional
	public void verify(String token) {
		String[] p = token == null ? new String[0] : token.split("\\.");
		if (p.length != 3) throw invalid();
		long userId;
		long exp;
		try {
			userId = Long.parseLong(p[0]);
			exp = Long.parseLong(p[1]);
		} catch (NumberFormatException e) {
			throw invalid();
		}
		User u = users.findById(userId).filter(x -> x.getDeletedAt() == null).orElseThrow(EmailVerification::invalid);
		if (!Hmac.matches(key, "verify-email:" + userId + ":" + u.getEmail() + ":" + exp, p[2])) throw invalid();
		if (clock.instant().getEpochSecond() > exp) {
			throw ApiException.badRequest("TOKEN_EXPIRED", "인증 링크가 만료되었습니다. My Page 에서 인증 메일을 다시 받아주세요.");
		}
		if (u.getEmailVerifiedAt() == null) u.verifyEmail(clock.instant());
	}

	private String sign(long userId, String email, long exp) {
		return Hmac.sign(key, "verify-email:" + userId + ":" + email + ":" + exp);
	}

	private static ApiException invalid() {
		return ApiException.badRequest("INVALID_TOKEN", "인증 링크가 올바르지 않습니다.");
	}
}
