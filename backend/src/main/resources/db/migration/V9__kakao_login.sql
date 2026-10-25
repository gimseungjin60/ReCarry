-- 카카오 로그인: 연결된 카카오 회원번호, 그리고 비밀번호를 직접 만든 계정인지 (카카오로 가입하면 false)
-- 새 테이블이 없으므로 RLS 변경은 없다 (users 는 V2 에서 이미 막혀 있다)
ALTER TABLE users ADD COLUMN kakao_id BIGINT UNIQUE;
ALTER TABLE users ADD COLUMN password_set BOOLEAN NOT NULL DEFAULT TRUE;
