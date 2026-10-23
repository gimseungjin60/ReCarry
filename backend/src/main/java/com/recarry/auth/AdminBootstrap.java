package com.recarry.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.recarry.common.AppProperties;
import com.recarry.user.Role;
import com.recarry.user.User;
import com.recarry.user.UserRepository;

/**
 * ADMIN_EMAIL + ADMIN_PASSWORD 가 모두 설정돼 있으면 기동 시 관리자를 만든다.
 * 이미 있는 계정이면 ADMIN_PASSWORD 가 그 계정 비밀번호와 같을 때만 권한을 올린다 (비밀번호는 바꾸지 않는다).
 * 비밀번호는 코드에도, 로그에도 남기지 않는다.
 */
@Component
@Profile("!dbcheck")   // 연결 점검 모드에서는 DB 에 쓰지 않는다
public class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final AppProperties props;
	private final UserRepository users;
	private final PasswordEncoder encoder;

	public AdminBootstrap(AppProperties props, UserRepository users, PasswordEncoder encoder) {
		this.props = props;
		this.users = users;
		this.encoder = encoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		AppProperties.Admin admin = props.admin();
		if (admin == null || !admin.configured()) return;

		String email = AuthService.normalizeEmail(admin.email());
		users.findByEmail(email).ifPresentOrElse(u -> {
			if (u.getRole() == Role.ADMIN) return;
			// 이메일 인증이 없으므로, 누군가 그 주소로 먼저 가입해 두었을 수 있다 — 비밀번호가 맞을 때만 올린다
			if (encoder.matches(admin.password(), u.getPasswordHash())) {
				u.promoteToAdmin();
				log.info("promoted existing user to ADMIN: {}", email);
			} else {
				log.warn("ADMIN_EMAIL belongs to an existing account with a different password — not promoted: {}", email);
			}
		}, () -> {
			users.save(new User(email, encoder.encode(admin.password()), "관리자", "", Role.ADMIN));
			log.info("created ADMIN account: {}", email);
		});
	}
}
