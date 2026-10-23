package com.recarry.auth;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 비밀번호 재설정 코드 — 해시만 저장한다. 한 번 쓰거나, 만료되거나, 5번 틀리면 끝. */
@Entity
@Table(name = "password_reset_codes")
public class PasswordResetCode {

	static final int MAX_ATTEMPTS = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "code_hash", nullable = false, updatable = false)
	private String codeHash;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "used_at")
	private Instant usedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected PasswordResetCode() {}

	PasswordResetCode(Long userId, String codeHash, Instant expiresAt) {
		this.userId = userId;
		this.codeHash = codeHash;
		this.expiresAt = expiresAt;
	}

	boolean usable(Instant now) {
		return usedAt == null && attempts < MAX_ATTEMPTS && now.isBefore(expiresAt);
	}

	void fail() {
		attempts++;
	}

	void use(Instant now) {
		usedAt = now;
	}

	String getCodeHash() { return codeHash; }
	Instant getCreatedAt() { return createdAt; }
}
