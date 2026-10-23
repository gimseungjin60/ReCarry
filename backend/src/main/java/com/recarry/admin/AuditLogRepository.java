package com.recarry.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	/** 조회 기록(_VIEW) */
	Page<AuditLog> findByActionEndingWith(String suffix, Pageable page);

	/** 변경 기록 (조회 제외) */
	Page<AuditLog> findByActionNotLike(String pattern, Pageable page);
}
