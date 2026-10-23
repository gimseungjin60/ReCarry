-- ============================================================
-- DEMO 데이터 문구 정정 (2026-10) — 운영자가 한 번 수동 실행한다. Flyway 가 아니다.
-- 조달 방향이 "공항 회수" → "중고 캐리어 매입" 으로 바뀌어(docs/POLICY.md), demo 카탈로그의 문구만 고친다.
-- 대상은 demo 캐리어(RC-<사이즈>-S<번호>)와 그 Story · 24" 상품 설명뿐. DELETE 없음. 여러 번 실행해도 결과가 같다.
--   psql "<Supabase 접속 정보>" -v ON_ERROR_STOP=1 -f backend/db/demo/fix_sourcing_copy_2026-10.sql
--   또는 Supabase Dashboard → SQL Editor
-- ============================================================

BEGIN;

UPDATE carriers SET collected_from = '중고 매입', updated_at = now()
WHERE carrier_code ~ '^RC-[0-9]{2}-S[0-9]{2}$'
  AND collected_from IN ('공항 인근', '숙박시설 보관 물품');

UPDATE carrier_events e SET title = '중고 매입 입고', detail = '쓰이지 않던 중고 캐리어를 매입'
FROM carriers c
WHERE e.carrier_id = c.id AND c.carrier_code ~ '^RC-[0-9]{2}-S[0-9]{2}$'
  AND e.type = 'COLLECTED' AND e.title IN ('공항 인근에서 회수', '숙박시설 보관 물품');

UPDATE carrier_models SET description = replace(description, '공항 인근에서 회수되어 ', '중고로 매입해 '), updated_at = now()
WHERE description LIKE '%공항 인근에서 회수되어 %';

COMMIT;
