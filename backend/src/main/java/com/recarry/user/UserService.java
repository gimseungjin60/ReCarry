package com.recarry.user;

import java.time.Clock;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recarry.booking.BookingService;
import com.recarry.common.ApiException;

/** 내 정보 수정 · 비밀번호 변경 · 탈퇴. 비밀번호 확인이 필요한 변경은 여기서 확인한다. */
@Service
@Transactional
public class UserService {

	private final UserRepository users;
	private final PasswordEncoder encoder;
	private final BookingService bookings;
	private final Clock clock;

	public UserService(UserRepository users, PasswordEncoder encoder, BookingService bookings, Clock clock) {
		this.users = users;
		this.encoder = encoder;
		this.bookings = bookings;
		this.clock = clock;
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
		checkPassword(u, current);
		u.changePassword(encoder.encode(next), clock.instant());
		return u;
	}

	public void withdraw(Long id, String password) {
		User u = get(id);
		checkPassword(u, password);
		bookings.closeForWithdrawal(id);
		// 아무도 모르는 비밀번호의 해시 — 탈퇴 계정으로는 로그인할 수 없다
		u.withdraw(encoder.encode(UUID.randomUUID().toString()), clock.instant());
		users.flush();
	}

	private void checkPassword(User u, String raw) {
		// 401 이 아니라 400 — 프론트는 401 을 "로그아웃됨"으로 처리한다
		if (!encoder.matches(raw, u.getPasswordHash())) {
			throw ApiException.badRequest("WRONG_PASSWORD", "현재 비밀번호가 올바르지 않습니다.");
		}
	}
}
