package com.recarry.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recarry.common.ApiException;
import com.recarry.common.AppProperties;
import com.recarry.common.Mailer;
import com.recarry.user.User;
import com.recarry.user.UserRepository;

/**
 * 비밀번호 찾기.
 * - 이메일로 6자리 코드(15분 유효)를 보내고, 코드 + 새 비밀번호로 재설정한다. 가입 여부는 응답으로 드러내지 않는다.
 * - 휴대폰 번호로는 가입한 이메일(일부 가림)을 찾는다. SMS 인증은 유료라 아직 쓰지 않는다 (docs/POLICY.md).
 */
@Service
public class PasswordResetService {

	private static final Duration TTL = Duration.ofMinutes(15);
	/** 계정당 하루 코드 발급 한도 — 코드당 5회 오답과 합쳐 하루 최대 25번만 맞혀볼 수 있다 */
	private static final int DAILY_CODES = 5;

	private final UserRepository users;
	private final PasswordResetCodeRepository codes;
	private final PasswordEncoder encoder;
	private final LoginRateLimiter limiter;
	private final Mailer mailer;
	private final AppProperties.Mail mail;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	PasswordResetService(UserRepository users, PasswordResetCodeRepository codes, PasswordEncoder encoder,
			LoginRateLimiter limiter, Mailer mailer, AppProperties props, Clock clock) {
		this.users = users;
		this.codes = codes;
		this.encoder = encoder;
		this.limiter = limiter;
		this.mailer = mailer;
		this.mail = props.mail();
		this.clock = clock;
	}

	/** 가입한 이메일이면 코드를 보낸다. 없는 이메일이어도 같은 응답 (가입 여부 비노출). */
	@Transactional
	public void request(String rawEmail) {
		// 메일을 보낼 수 없는 환경이면 모든 요청에 똑같이 알린다 (계정 유무와 무관하게)
		if (!mail.configured() && !mail.logOnly()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE",
				"지금은 이메일을 보낼 수 없습니다. 고객센터로 문의해주세요.");
		}
		String email = AuthService.normalizeEmail(rawEmail);
		limiter.consume("reset-e:" + email, 3);

		User user = users.findByEmail(email).filter(u -> u.getDeletedAt() == null).orElse(null);
		if (user == null) return;

		Instant now = clock.instant();
		// 하루 한도를 넘으면 조용히 보내지 않는다 (응답을 다르게 하면 가입 여부가 드러난다)
		if (codes.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofDays(1))) >= DAILY_CODES) return;
		codes.closeOpen(user.getId(), now);
		String code = String.format("%06d", random.nextInt(1_000_000));
		codes.save(new PasswordResetCode(user.getId(), hash(user.getId(), code), now.plus(TTL)));
		// 커밋 뒤 백그라운드로 보낸다 — 메일 API 시간(최대 10초)이 응답 시간에 실리면 가입 여부가 드러난다.
		// 발송 실패도 응답으로 알리지 않는다 — 사용자는 다시 요청할 수 있다
		mailer.sendAfterCommit(email, "[RECARRY] 비밀번호 재설정 인증 코드",
			"RECARRY 비밀번호 재설정 인증 코드: " + code + "\n\n"
				+ "15분 동안 유효합니다. 요청하지 않았다면 이 메일을 무시하세요. 비밀번호는 바뀌지 않습니다.");
	}

	/** 코드가 맞으면 비밀번호를 바꾸고 모든 기기의 로그인을 끊는다. 틀린 횟수는 롤백되지 않게 남긴다. */
	@Transactional(noRollbackFor = ApiException.class)
	public void confirm(String rawEmail, String code, String newPassword) {
		String email = AuthService.normalizeEmail(rawEmail);
		limiter.consume("reset-c:" + email, 10);
		Instant now = clock.instant();

		User user = users.findByEmail(email).filter(u -> u.getDeletedAt() == null).orElse(null);
		PasswordResetCode c = user == null ? null
			: codes.findFirstByUserIdOrderByIdDesc(user.getId()).filter(x -> x.usable(now)).orElse(null);
		if (c == null) throw invalid();
		if (!MessageDigest.isEqual(c.getCodeHash().getBytes(StandardCharsets.US_ASCII),
				hash(user.getId(), code).getBytes(StandardCharsets.US_ASCII))) {
			c.fail();
			throw invalid();
		}
		c.use(now);
		user.changePassword(encoder.encode(newPassword), now);
		limiter.recordSuccess(email);   // 로그인 실패로 막혀 있었다면 풀어준다
	}

	/**
	 * 이름 + 휴대폰 번호로 가입한 이메일 찾기 (앞 2글자만 보여준다).
	 * 같은 이름+번호 조합은 5번, 서비스 전체는 15분에 300번까지 — 대량 대입으로 이메일을 모으지 못하게.
	 * ponytail: 전체 한도는 남용 시 이 기능만 잠시 막힌다 (로그인 · 재설정에는 영향 없음).
	 */
	@Transactional(readOnly = true)
	public List<String> findEmails(String name, String phone) {
		String digits = phone.replaceAll("[^0-9]", "");
		if (digits.length() < 9) return List.of();
		limiter.consume("find-k:" + name.trim() + ":" + digits, 5);
		limiter.consume("find-all", 300);
		return users.findEmailsByNameAndPhoneDigits(name.trim(), digits).stream().map(PasswordResetService::mask).toList();
	}

	static String mask(String email) {
		int at = email.indexOf('@');
		String local = email.substring(0, at);
		int keep = local.length() <= 2 ? 1 : 2;
		return local.substring(0, keep) + "***" + email.substring(at);
	}

	private static ApiException invalid() {
		return ApiException.badRequest("INVALID_CODE", "인증 코드가 올바르지 않거나 만료되었습니다. 코드를 다시 요청해주세요.");
	}

	/** 사용자 id 를 섞은 SHA-256 — 같은 코드라도 사용자마다 해시가 다르다 */
	private static String hash(Long userId, String code) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-256").digest((userId + ":" + code).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(d);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
