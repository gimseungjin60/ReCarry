package com.recarry.admin;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 관리자 변경 기록 한 줄 — 누가 · 언제 · 무엇을 바꿨나. 수정하지 않고 쌓기만 한다. */
@Entity
@Table(name = "admin_audit_logs")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "admin_id", updatable = false)
	private Long adminId;

	@Column(nullable = false, updatable = false)
	private String action;

	@Column(nullable = false, updatable = false)
	private String target;

	@Column(nullable = false, updatable = false)
	private String detail;

	/** 접속지 — 프록시 뒤라 참고용 (X-Forwarded-For 첫 값 또는 연결 주소) */
	@Column(nullable = false, updatable = false)
	private String ip;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AuditLog() {}

	AuditLog(Long adminId, String action, String target, String detail, String ip) {
		this.adminId = adminId;
		this.action = action;
		this.target = target.length() > 40 ? target.substring(0, 40) : target;
		this.detail = detail.length() > 300 ? detail.substring(0, 300) : detail;
		this.ip = ip.length() > 45 ? ip.substring(0, 45) : ip;
	}

	public Long getAdminId() { return adminId; }
	public String getAction() { return action; }
	public String getTarget() { return target; }
	public String getDetail() { return detail; }
	public Instant getCreatedAt() { return createdAt; }
	public String getIp() { return ip; }
}
