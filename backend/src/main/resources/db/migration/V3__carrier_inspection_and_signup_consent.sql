-- V3: 캐리어별 검수 항목 결과 + 가입 시 필수 약관 동의 기록.
-- 사업 값(검수 결과 · 약관 버전)은 여기 넣지 않는다.

-- ---------------------------------------------------------------- 검수 항목
-- 캐리어 한 대의 "현재" 검수 결과. 검수일은 carriers.inspected_at, 과정 기록은 carrier_events(INSPECTION).
-- 항목 목록은 화면의 검수 체크리스트(세척 · 외관 · 바퀴 · 손잡이 · 지퍼)와 같다.
CREATE TABLE carrier_inspection_checks (
    carrier_id BIGINT       NOT NULL REFERENCES carriers (id) ON DELETE CASCADE,
    item       VARCHAR(12)  NOT NULL CHECK (item IN ('CLEANING', 'EXTERIOR', 'WHEELS', 'HANDLE', 'ZIPPER')),
    passed     BOOLEAN      NOT NULL,
    note       VARCHAR(120) NOT NULL DEFAULT '',
    PRIMARY KEY (carrier_id, item)
);

-- ---------------------------------------------------------------- 약관 동의
-- 동의한 문서 버전과 시각. 이 migration 전에 가입한 계정은 기록이 없다 (NULL).
ALTER TABLE users
    ADD COLUMN terms_version   VARCHAR(20),
    ADD COLUMN privacy_version VARCHAR(20),
    ADD COLUMN agreed_at       TIMESTAMPTZ,
    ADD CONSTRAINT users_consent_complete CHECK (
        (terms_version IS NULL AND privacy_version IS NULL AND agreed_at IS NULL)
        OR (terms_version IS NOT NULL AND privacy_version IS NOT NULL AND agreed_at IS NOT NULL));

-- ---------------------------------------------------------------- Supabase Data API 차단 (V2 와 같은 규칙)
ALTER TABLE carrier_inspection_checks ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    r text;
BEGIN
    FOREACH r IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            EXECUTE format('REVOKE ALL ON carrier_inspection_checks FROM %I', r);
        END IF;
    END LOOP;
END $$;
