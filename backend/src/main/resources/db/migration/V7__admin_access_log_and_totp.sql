-- V7: 관리자 접속기록(고객정보 조회 포함) · 관리자 2단계 인증(TOTP).
-- 개인정보 안전성 확보조치 기준: 개인정보취급자의 접속기록(누가 · 언제 · 어디서 · 무엇을)을 1년 이상 보관한다.
-- admin_audit_logs 는 삭제하지 않는다 (자동 파기 없음 → 1년 이상 보관 충족).
ALTER TABLE admin_audit_logs ADD COLUMN ip VARCHAR(45) NOT NULL DEFAULT '';

-- TOTP (RFC 6238, 30초). secret 은 Base32. 마지막으로 쓴 시간 단계를 남겨 같은 코드 재사용을 막는다.
ALTER TABLE users
    ADD COLUMN totp_secret    VARCHAR(64),
    ADD COLUMN totp_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN totp_last_step BIGINT;
