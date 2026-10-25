package com.recarry.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	/** email 은 호출하는 쪽에서 소문자로 정규화해서 넘긴다. */
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	Optional<User> findByKakaoId(Long kakaoId);

	/** 이메일 찾기 — 이름이 같고 휴대폰 번호의 숫자가 같은 활성 계정 (하이픈·공백 무시) */
	@Query(value = """
		SELECT email FROM users
		WHERE deleted_at IS NULL AND role = 'USER' AND name = :name AND regexp_replace(phone, '[^0-9]', '', 'g') = :digits
		ORDER BY id
		""", nativeQuery = true)
	java.util.List<String> findEmailsByNameAndPhoneDigits(@Param("name") String name, @Param("digits") String digits);

	/** 토큰 검증용 — 요청마다 부르므로 필요한 컬럼만 읽는다 */
	@Query("select u.role as role, u.deletedAt as deletedAt, u.sessionsValidAfter as sessionsValidAfter from User u where u.id = :id")
	Optional<SessionState> findSessionState(@Param("id") Long id);

	interface SessionState {
		Role getRole();
		java.time.Instant getDeletedAt();
		java.time.Instant getSessionsValidAfter();
	}
}
