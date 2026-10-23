package com.recarry.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

	/** 사용자의 마지막 코드 (유효한 것은 이것 하나뿐이다). 시도 횟수를 올리므로 행을 잠근다 — 동시 대입이 횟수를 건너뛰지 못하게 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<PasswordResetCode> findFirstByUserIdOrderByIdDesc(Long userId);

	/** 하루 발급 한도 — 메모리 카운터와 달리 재시작해도 남는다 */
	long countByUserIdAndCreatedAtAfter(Long userId, Instant since);

	/** 새 코드를 보내기 전에 아직 열려 있는 이전 코드를 닫는다 */
	@Modifying
	@Query("update PasswordResetCode c set c.usedAt = :now where c.userId = :userId and c.usedAt is null")
	void closeOpen(@Param("userId") Long userId, @Param("now") Instant now);
}
