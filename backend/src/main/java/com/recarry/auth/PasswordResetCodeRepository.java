package com.recarry.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

	/** 사용자의 마지막 코드 (유효한 것은 이것 하나뿐이다) */
	Optional<PasswordResetCode> findFirstByUserIdOrderByIdDesc(Long userId);

	/** 새 코드를 보내기 전에 아직 열려 있는 이전 코드를 닫는다 */
	@Modifying
	@Query("update PasswordResetCode c set c.usedAt = :now where c.userId = :userId and c.usedAt is null")
	void closeOpen(@Param("userId") Long userId, @Param("now") Instant now);
}
