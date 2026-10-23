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

	/** 탈퇴 계정의 비식별 이메일 도메인 — 가입에 쓸 수 없다 (AuthService) */
	public static final String WITHDRAWN_DOMAIN = "deleted.recarry";

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

	/** 탈퇴 시각. 탈퇴하면 이메일·이름·연락처는 비식별 값으로 바뀐다 */
	@Column(name = "deleted_at")
	private Instant deletedAt;

	/** 이 시각 이전에 발급된 로그인 토큰은 무효 (비밀번호 변경 · 탈퇴) */
	@Column(name = "sessions_valid_after")
	private Instant sessionsValidAfter;

	/** 관리자 2단계 인증(TOTP) — 켜져 있으면 로그인에 OTP 코드가 필요하다 */
	@Column(name = "totp_secret")
	private String totpSecret;

	@Column(name = "totp_enabled", nullable = false)
	private boolean totpEnabled;

	/** 마지막으로 받아들인 TOTP 시간 단계 — 같은 코드 재사용 방지 */
	@Column(name = "totp_last_step")
	private Long totpLastStep;

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

	/** 일반 회원이 지금 시행 중인 문서 버전에 동의하지 않았는가 (관리자는 운영자라 제외) */
	public boolean needsConsent(com.recarry.common.AppProperties.Legal legal) {
		return role == Role.USER
			&& !(legal.termsVersion().equals(termsVersion) && legal.privacyVersion().equals(privacyVersion));
	}

	public void agree(String termsVersion, String privacyVersion, Instant at) {
		this.termsVersion = termsVersion;
		this.privacyVersion = privacyVersion;
		this.agreedAt = at;
	}

	public Instant getDeletedAt() { return deletedAt; }

	public void updateProfile(String name, String phone) {
		this.name = name;
		this.phone = phone;
	}

	/** 비밀번호를 바꾸면 다른 기기의 로그인은 모두 끊긴다 */
	public void changePassword(String passwordHash, Instant now) {
		this.passwordHash = passwordHash;
		this.sessionsValidAfter = now;
	}

	/**
	 * 탈퇴: 개인정보를 비식별 값으로 바꾸고 모든 로그인을 끊는다. 행은 예약 기록(FK) 때문에 남긴다.
	 * 같은 이메일로 다시 가입할 수 있다. 약관 동의 기록(버전 · 시각)은 개인정보가 아니므로 남긴다.
	 */
	public void withdraw(String unusablePasswordHash, Instant now) {
		this.email = "deleted-" + id + "@" + WITHDRAWN_DOMAIN;
		this.name = "탈퇴회원";
		this.phone = "";
		this.passwordHash = unusablePasswordHash;
		this.deletedAt = now;
		this.sessionsValidAfter = now;
	}

	public String getTotpSecret() { return totpSecret; }
	public boolean isTotpEnabled() { return totpEnabled; }
	public Long getTotpLastStep() { return totpLastStep; }

	/** 새 secret 을 준비한다 (OTP 앱에 등록하고 코드를 확인하기 전까지는 켜지지 않는다) */
	public void prepareTotp(String secret) {
		this.totpSecret = secret;
		this.totpEnabled = false;
		this.totpLastStep = null;
	}

	public void enableTotp(long usedStep) {
		this.totpEnabled = true;
		this.totpLastStep = usedStep;
	}

	public void useTotp(long usedStep) {
		this.totpLastStep = usedStep;
	}

	public void disableTotp() {
		this.totpSecret = null;
		this.totpEnabled = false;
		this.totpLastStep = null;
	}

	public void promoteToAdmin() {
		this.role = Role.ADMIN;
	}
}
