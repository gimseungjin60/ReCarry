package com.recarry.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.recarry.common.ApiException;
import com.recarry.common.AppProperties;
import com.recarry.common.Mailer;
import com.recarry.user.UserRepository;

/**
 * 가입 이메일 인증번호 (쇼핑몰식 "인증번호 받기"). DB 에 저장하지 않는다:
 * 메일로 6자리 번호를 보내고, 브라우저에는 challenge = 만료시각.HMAC(이메일 · 번호 · 만료시각) 만 준다.
 * 서버 키 없이는 challenge 로 번호를 알아낼 수 없고, 맞춰보기는 이메일당 15분에 5번까지다.
 */
@Service
public class SignupCodes {

	private static final Duration TTL = Duration.ofMinutes(10);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final UserRepository users;
	private final SecretKey key;
	private final Mailer mailer;
	private final AppProperties props;
	private final LoginRateLimiter limiter;
	private final Clock clock;

	public SignupCodes(UserRepository users, SecretKey jwtKey, Mailer mailer, AppProperties props,
			LoginRateLimiter limiter, Clock clock) {
		this.users = users;
		this.key = jwtKey;
		this.mailer = mailer;
		this.props = props;
		this.limiter = limiter;
		this.clock = clock;
	}

	/** 메일을 보낼 수 있고 인증을 요구하는 환경에서만 가입에 인증번호가 필요하다 */
	public boolean enabled() {
		return props.mail().requireVerifiedEmail() && props.mail().canSend();
	}

	/** 인증번호를 메일로 보내고 challenge 를 돌려준다 (이메일당 15분에 3번) */
	public String issue(String rawEmail) {
		if (!enabled()) throw ApiException.badRequest("EMAIL_CODE_DISABLED", "지금은 이메일 인증번호를 보낼 수 없습니다.");
		String email = AuthService.normalizeEmail(rawEmail);
		AuthService.checkAvailable(email, users, "이미 가입된 이메일입니다.");
		limiter.consume("signup-code:" + email, 3);
		String code = String.format("%06d", RANDOM.nextInt(1_000_000));
		long exp = clock.instant().plus(TTL).getEpochSecond();
		mailer.sendAfterCommit(email, "[RECARRY] 회원가입 인증번호",
			"RECARRY 회원가입 인증번호는 " + code + " 입니다. 10분 안에 가입 화면에 입력해주세요.\n\n본인이 요청하지 않았다면 이 메일을 무시하세요.");
		return exp + "." + Hmac.sign(key, data(email, code, exp));
	}

	/**
	 * 가입 요청의 인증번호를 확인한다. 인증을 요구하지 않는 환경이면 false (인증하지 않은 가입).
	 * 틀리면 400 + fields.emailCode.
	 */
	public boolean check(String email, String challenge, String code) {
		if (!enabled()) return false;
		if (challenge == null || challenge.isBlank() || code == null || code.isBlank()) {
			throw error("INVALID_CODE", "이메일 인증번호를 받아 입력해주세요.");
		}
		limiter.consume("signup-code-try:" + email, 5);
		String[] p = challenge.split("\\.", 2);
		long exp;
		try {
			exp = Long.parseLong(p[0]);
		} catch (NumberFormatException e) {
			throw invalid();
		}
		if (p.length != 2 || !Hmac.matches(key, data(email, code.trim(), exp), p[1])) throw invalid();
		if (clock.instant().getEpochSecond() > exp) throw error("CODE_EXPIRED", "인증번호가 만료되었습니다. 다시 받아주세요.");
		return true;
	}

	private static String data(String email, String code, long exp) {
		return "signup-code:" + email + ":" + code + ":" + exp;
	}

	private static ApiException invalid() {
		return error("INVALID_CODE", "인증번호가 올바르지 않습니다.");
	}

	private static ApiException error(String code, String message) {
		return ApiException.badRequest(code, message).field("emailCode");
	}
}
