package com.recarry.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	/** email 은 호출하는 쪽에서 소문자로 정규화해서 넘긴다. */
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	/** 토큰 검증용 — 요청마다 부르므로 필요한 컬럼만 읽는다 */
	@Query("select u.role as role, u.deletedAt as deletedAt, u.sessionsValidAfter as sessionsValidAfter from User u where u.id = :id")
	Optional<SessionState> findSessionState(@Param("id") Long id);

	interface SessionState {
		Role getRole();
		java.time.Instant getDeletedAt();
		java.time.Instant getSessionsValidAfter();
	}
}
