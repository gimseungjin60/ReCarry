-- ============================================================
-- SAMPLE CATALOG — 실제 운영 데이터가 아니다.
-- Frontend v1 의 src/data/carriers.ts 값을 그대로 옮긴 웹 MVP 검증용 데이터.
-- 가격 · 추가 요금 · 회수처 · 수리 이력 · 검수일 · 대수 는 확정된 사업 정보가 아니다.
-- local 프로필(db/sample 위치)에서만 적용된다. 운영 DB 에는 넣지 않는다.
--
-- repeatable migration: 파일이 바뀌면 다시 실행된다.
--   모델 정보는 덮어쓰고, 캐리어는 이미 있으면 건드리지 않는다 (관리자가 바꾼 상태를 보존).
-- 대표 캐리어(featured) 외의 추가 대수는 v1 의 "N대 이용 가능" 을 재현하기 위한 것으로,
-- Carrier ID 에 S(sample) 를 붙여 실제 ID 와 구분한다.
-- ============================================================

INSERT INTO carrier_models (size, name, inch, capacity, usage, dims, weight, price, extra_night_price, headline, description)
VALUES
  ('20', 'RECARRY 20"', '20-inch', '38L', '기내 반입 · 1–3박', '36 × 23 × 55 cm', '2.9 kg', 19900, 4000,
   E'기내에 그대로,\n짧은 여행을 위한 크기.',
   '국내선·단거리 해외 기내 반입 규격. 가볍고 단단한 폴리카보네이트 쉘에, 교체한 지퍼와 새 바퀴로 처음처럼 부드럽게 움직입니다.'),
  ('24', 'RECARRY 24"', '24-inch', '64L', '위탁 수하물 · 3–5박', '44 × 27 × 66 cm', '3.8 kg', 25900, 5000,
   E'가볍고 단단한,\n가장 많이 찾는 사이즈.',
   '3–5박 해외여행에 알맞은 위탁 수하물 사이즈. 공항 인근에서 회수되어 바퀴 두 개와 손잡이 레일을 교체하고, 5개 항목 검수를 통과했습니다.'),
  ('28', 'RECARRY 28"', '28-inch', '96L', '장기 여행 · 7박 이상', '50 × 30 × 76 cm', '4.6 kg', 31900, 6000,
   E'긴 여행도\n한 번에.',
   '일주일 이상의 여행과 이사, 유학 준비에 알맞은 대형 사이즈. 외관에 미세한 흔적이 남아 B GRADE로 분류했지만, 기능 검수는 A와 같은 기준을 통과했습니다.')
ON CONFLICT (size) DO UPDATE SET
  name = EXCLUDED.name, inch = EXCLUDED.inch, capacity = EXCLUDED.capacity, usage = EXCLUDED.usage,
  dims = EXCLUDED.dims, weight = EXCLUDED.weight, price = EXCLUDED.price,
  extra_night_price = EXCLUDED.extra_night_price, headline = EXCLUDED.headline,
  description = EXCLUDED.description, updated_at = now();

INSERT INTO carriers (model_id, carrier_code, grade, status, collected_from, repair_summary, inspected_at, featured)
SELECT m.id, c.code, c.grade, 'AVAILABLE', c.collected_from, c.repair, c.inspected::date, c.featured
FROM (VALUES
  ('20', 'RC-20-0412', 'A', '공항 인근',         '지퍼 슬라이더 교체',   '2026-04-05', TRUE),
  ('20', 'RC-20-S01',  'A', '공항 인근',         '바퀴 베어링 윤활',     '2026-04-05', FALSE),
  ('20', 'RC-20-S02',  'A', '공항 인근',         '손잡이 나사 조임',     '2026-04-05', FALSE),
  ('24', 'RC-24-0187', 'A', '공항 인근',         '바퀴 ×2, 손잡이 레일', '2026-03-18', TRUE),
  ('24', 'RC-24-S01',  'A', '공항 인근',         '바퀴 ×1',              '2026-03-18', FALSE),
  ('28', 'RC-28-0093', 'B', '숙박시설 보관 물품', '바퀴 ×4, 모서리 가드', '2026-02-25', TRUE)
) AS c(size, code, grade, collected_from, repair, inspected, featured)
JOIN carrier_models m ON m.size = c.size
ON CONFLICT (carrier_code) DO NOTHING;

-- 대표 캐리어의 Story 타임라인 (이미 기록이 있는 캐리어는 건너뛴다)
INSERT INTO carrier_events (carrier_id, event_date, type, title, detail)
SELECT c.id, e.d::date, e.type, e.title, e.detail
FROM (VALUES
  ('RC-20-0412', '2026-04-02', 'COLLECTED',  '공항 인근에서 회수', '폐기 예정이던 캐리어를 인수'),
  ('RC-20-0412', '2026-04-03', 'REPAIR',     '점검 및 수리',       '지퍼 슬라이더 교체, 바퀴 베어링 윤활'),
  ('RC-20-0412', '2026-04-04', 'CLEANING',   '세척',               '내부 원단 세척, 외부 쉘 광택, 살균·탈취'),
  ('RC-20-0412', '2026-04-05', 'INSPECTION', '검수 완료',          '5개 항목 통과 · A GRADE'),
  ('RC-20-0412', '2026-04-09', 'TRIP',       '새로운 여행',        '서울 → 오사카, 2박 3일'),
  ('RC-24-0187', '2026-03-14', 'COLLECTED',  '공항 인근에서 회수', '폐기 예정이던 캐리어를 인수'),
  ('RC-24-0187', '2026-03-16', 'REPAIR',     '점검 및 수리',       '바퀴 2개 교체, 손잡이 레일 교정'),
  ('RC-24-0187', '2026-03-17', 'CLEANING',   '세척',               '내부 원단 세척, 외부 쉘 광택, 살균·탈취'),
  ('RC-24-0187', '2026-03-18', 'INSPECTION', '검수 완료',          '5개 항목 통과 · A GRADE'),
  ('RC-24-0187', '2026-03-22', 'TRIP',       '새로운 여행',        '서울 → 제주, 3박 4일'),
  ('RC-28-0093', '2026-02-20', 'COLLECTED',  '숙박시설 보관 물품', '보관 기한이 끝난 캐리어를 기증받아 인수'),
  ('RC-28-0093', '2026-02-23', 'REPAIR',     '점검 및 수리',       '바퀴 4개 전체 교체, 모서리 가드 교체'),
  ('RC-28-0093', '2026-02-24', 'CLEANING',   '세척',               '내부 원단 세척, 외부 쉘 광택, 살균·탈취'),
  ('RC-28-0093', '2026-02-25', 'INSPECTION', '검수 완료',          '5개 항목 통과 · 외관 흔적으로 B GRADE'),
  ('RC-28-0093', '2026-03-02', 'TRIP',       '새로운 여행',        '인천 → 리스본, 9박 10일')
) AS e(code, d, type, title, detail)
JOIN carriers c ON c.carrier_code = e.code
WHERE NOT EXISTS (SELECT 1 FROM carrier_events x WHERE x.carrier_id = c.id);
