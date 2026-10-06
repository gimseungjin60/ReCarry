package com.recarry.user;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 항상 소문자로 저장한다 (DB 에도 lower(email) unique 인덱스). */
	@Column(nullable = false)
	private String email;

	/** BCrypt 해시. 평문은 어디에도 저장하지 않는다. */
	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String phone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role = Role.USER;

	/** 가입 때 동의한 이용약관 · 개인정보 처리방침 버전과 시각. 동의 기록 이전 가입자·bootstrap 관리자는 null */
	@Column(name = "terms_version")
	private String termsVersion;

	@Column(name = "privacy_version")
	private String privacyVersion;

	@Column(name = "agreed_at")
	private Instant agreedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected User() {}

	public User(String email, String passwordHash, String name, String phone, Role role) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.name = name;
		this.phone = phone;
		this.role = role;
	}

	public Long getId() { return id; }
	public String getEmail() { return email; }
	public String getPasswordHash() { return passwordHash; }
	public String getName() { return name; }
	public String getPhone() { return phone; }
	public Role getRole() { return role; }
	public Instant getCreatedAt() { return createdAt; }

	public Instant getAgreedAt() { return agreedAt; }

	public void agree(String termsVersion, String privacyVersion, Instant at) {
		this.termsVersion = termsVersion;
		this.privacyVersion = privacyVersion;
		this.agreedAt = at;
	}

	public void promoteToAdmin() {
		this.role = Role.ADMIN;
	}
}
