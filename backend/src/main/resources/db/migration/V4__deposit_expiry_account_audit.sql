-- V4: 보증금 스냅샷 · 예약 요청 만료/취소 사유 · 회원 탈퇴/세션 무효화 · 관리자 변경 기록.
-- 정책 값(보증금 비율 · 만료 기간)은 application.yml 에 있다. 결정 기록: docs/POLICY.md

-- ---------------------------------------------------------------- bookings
-- 보증금: 예약 시점에 서버가 계산한 금액 (대여 요금과 별도, 반납 후 환불 대상). 이전 예약은 0.
-- cancel_reason: 누가/왜 취소했는가 — USER(고객) · ADMIN(운영자) · EXPIRED(요청 기한 만료)
ALTER TABLE bookings
    ADD COLUMN deposit       INTEGER     NOT NULL DEFAULT 0 CHECK (deposit >= 0),
    ADD COLUMN cancel_reason VARCHAR(10) CHECK (cancel_reason IN ('USER', 'ADMIN', 'EXPIRED'));
-- 만료 대상 찾기 (status = REQUESTED 인 오래된 요청)
CREATE INDEX bookings_requested_created_idx ON bookings (created_at) WHERE status = 'REQUESTED';

-- ---------------------------------------------------------------- users
-- deleted_at: 탈퇴 시각. 탈퇴하면 개인정보 컬럼은 비식별 값으로 바뀐다 (예약 기록의 FK 는 유지).
-- sessions_valid_after: 이 시각 이전에 발급된 로그인 토큰은 무효 (비밀번호 변경 · 탈퇴 · 권한 변경).
ALTER TABLE users
    ADD COLUMN deleted_at           TIMESTAMPTZ,
    ADD COLUMN sessions_valid_after TIMESTAMPTZ;

-- ---------------------------------------------------------------- 관리자 변경 기록
CREATE TABLE admin_audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    admin_id    BIGINT       REFERENCES users (id),
    action      VARCHAR(40)  NOT NULL,       -- 예: CARRIER_STATUS, BOOKING_STATUS
    target      VARCHAR(40)  NOT NULL,       -- Carrier ID · 예약번호 · 사이즈
    detail      VARCHAR(300) NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX admin_audit_logs_created_idx ON admin_audit_logs (created_at DESC);

-- ---------------------------------------------------------------- Supabase Data API 차단 (V2 와 같은 규칙)
ALTER TABLE admin_audit_logs ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    r text;
BEGIN
    FOREACH r IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            EXECUTE format('REVOKE ALL ON admin_audit_logs FROM %I', r);
            EXECUTE format('REVOKE ALL ON SEQUENCE admin_audit_logs_id_seq FROM %I', r);
        END IF;
    END LOOP;
END $$;
