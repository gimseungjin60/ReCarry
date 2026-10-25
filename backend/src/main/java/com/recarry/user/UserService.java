package com.recarry.user;

import java.time.Clock;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recarry.booking.BookingService;
import com.recarry.common.ApiException;
import com.recarry.common.AppProperties;

/** 내 정보 수정 · 비밀번호 변경 · 탈퇴. 비밀번호 확인이 필요한 변경은 여기서 확인한다. */
@Service
@Transactional
public class UserService {

	private final UserRepository users;
	private final PasswordEncoder encoder;
	private final BookingService bookings;
	private final Clock clock;
	private final AppProperties props;

	public UserService(UserRepository users, PasswordEncoder encoder, BookingService bookings, Clock clock,
			AppProperties props) {
		this.users = users;
		this.encoder = encoder;
		this.bookings = bookings;
		this.clock = clock;
		this.props = props;
	}

	public UserResponse respond(User u) {
		return UserResponse.of(u, props);
	}

	/** 시행 중인 문서 버전에 다시 동의 (이전 버전에 동의했거나 기록이 없는 회원) */
	public User agreeToCurrent(Long id) {
		User u = get(id);
		u.agree(props.legal().termsVersion(), props.legal().privacyVersion(), clock.instant());
		return u;
	}

	@Transactional(readOnly = true)
	public User get(Long id) {
		return users.findById(id)
			.filter(u -> u.getDeletedAt() == null)
			.orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
	}

	public User updateProfile(Long id, String name, String phone) {
		User u = get(id);
		u.updateProfile(name.trim(), phone.trim());
		return u;
	}

	/** 다른 기기의 로그인은 끊긴다. 호출한 쪽이 새 토큰을 발급한다. */
	public User changePassword(Long id, String current, String next) {
		User u = get(id);
		if (!u.isPasswordSet()) {
			throw ApiException.badRequest("NO_PASSWORD", "카카오로 가입한 계정입니다. 비밀번호 찾기로 비밀번호를 만들 수 있습니다.");
		}
		checkPassword(u, current);
		u.changePassword(encoder.encode(next), clock.instant());
		return u;
	}

	public void withdraw(Long id, String password) {
		User u = get(id);
		// 카카오로 가입해 비밀번호가 없는 계정은 로그인한 세션만으로 탈퇴한다
		if (u.isPasswordSet()) checkPassword(u, password);
		if (u.getRole() == Role.ADMIN) {
			// 마지막 관리자가 사라지는 것을 막는다 — 관리자 계정 정리는 운영자가 DB/환경변수로 한다
			throw ApiException.conflict("ADMIN_CANNOT_WITHDRAW", "관리자 계정은 탈퇴할 수 없습니다.");
		}
		bookings.closeForWithdrawal(id);
		// 아무도 모르는 비밀번호의 해시 — 탈퇴 계정으로는 로그인할 수 없다
		u.withdraw(encoder.encode(UUID.randomUUID().toString()), clock.instant());
		users.flush();
	}

	private void checkPassword(User u, String raw) {
		// 401 이 아니라 400 — 프론트는 401 을 "로그아웃됨"으로 처리한다
		if (raw == null || !encoder.matches(raw, u.getPasswordHash())) {
			throw ApiException.badRequest("WRONG_PASSWORD", "현재 비밀번호가 올바르지 않습니다.");
		}
	}
}
