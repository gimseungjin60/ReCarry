-- Supabase 는 public 스키마를 Data API(PostgREST)로 자동 공개하고,
-- 기본 권한으로 anon · authenticated 역할에 새 테이블의 모든 권한을 준다.
-- RECARRY 는 Data API 를 쓰지 않는다 (모든 접근은 Spring backend 경유).
-- 공개 anon 키로 users(비밀번호 해시) · bookings(주소·연락처)를 읽고 쓸 수 없도록 막는다.
--
-- 1) 모든 테이블에 RLS — 정책이 없으므로 Data API 역할은 행을 하나도 볼 수 없다.
--    backend 는 테이블 소유자(postgres / 로컬 recarry)로 접속하므로 RLS 를 적용받지 않는다.
-- 2) anon · authenticated 권한 회수 (+ 앞으로 만들 객체의 기본 권한도). 이 역할이 없는 로컬 DB 에서는 건너뛴다.
--
-- flyway_schema_history 는 여기서 건드리지 않는다 — migration 도중에는 Flyway 가 그 테이블을 잠그고 있어
-- 같은 migration 안에서 바꾸면 서로 기다리다 멈춘다. prod 기동 시 SupabaseLockdown 이 migration 뒤에 처리한다.
--
-- 새 테이블을 만드는 migration 은 반드시 ENABLE ROW LEVEL SECURITY 를 함께 넣는다.

ALTER TABLE users          ENABLE ROW LEVEL SECURITY;
ALTER TABLE carrier_models ENABLE ROW LEVEL SECURITY;
ALTER TABLE carriers       ENABLE ROW LEVEL SECURITY;
ALTER TABLE carrier_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE bookings       ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    r text;
BEGIN
    FOREACH r IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            EXECUTE format('REVOKE ALL ON users, carrier_models, carriers, carrier_events, bookings FROM %I', r);
            EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', r);
            EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public REVOKE ALL ON TABLES FROM %I', current_user, r);
            EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public REVOKE ALL ON SEQUENCES FROM %I', current_user, r);
            EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM %I', current_user, r);
        END IF;
    END LOOP;
END $$;
