-- V6: 비밀번호 재설정 코드. 이메일로 보낸 6자리 코드의 해시만 저장한다 (원문 저장 금지).
-- 사용자당 유효한 코드는 마지막 하나 — 새로 요청하면 이전 코드는 used_at 으로 닫는다.
CREATE TABLE password_reset_codes (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL,              -- SHA-256 hex
    expires_at TIMESTAMPTZ NOT NULL,
    attempts   INTEGER     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX password_reset_codes_user_idx ON password_reset_codes (user_id, created_at DESC);

-- Supabase Data API 차단 (V2 와 같은 규칙)
ALTER TABLE password_reset_codes ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    r text;
BEGIN
    FOREACH r IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            EXECUTE format('REVOKE ALL ON password_reset_codes FROM %I', r);
            EXECUTE format('REVOKE ALL ON SEQUENCE password_reset_codes_id_seq FROM %I', r);
        END IF;
    END LOOP;
END $$;
