/* ============================================================
   캐리어 화면 정의.
   상품 · 가격 · Carrier ID · 회수처 · 수리 이력 · 재고는 더 이상 여기 없다 —
   backend API(GET /api/carriers)에서 온다. sample 카탈로그는
   backend/src/main/resources/db/sample/R__sample_catalog.sql 한 곳에만 있다.
   여기에는 화면 전용 값(일러스트 색·크기)과 고정 UI 문구만 둔다. (CLAUDE.md §5)
   ============================================================ */

import type { BagColor, CarrierSize } from './types'

/** 사이즈별 일러스트 표현. 실사진이 생기면 photo 만 채운다. */
export const PRESENTATION: Record<
  CarrierSize,
  { color: BagColor; bg: string; photo: string | null; photoPos: string; bagWidth: number; bagHeight: number }
> = {
  '20': { color: 'chalk', bg: 'var(--stone)', photo: null, photoPos: '50% 50%', bagWidth: 132, bagHeight: 206 },
  '24': { color: 'graphite', bg: 'var(--sand)', photo: null, photoPos: '50% 50%', bagWidth: 160, bagHeight: 250 },
  '28': { color: 'clay', bg: 'var(--canvas)', photo: null, photoPos: '50% 50%', bagWidth: 178, bagHeight: 284 },
}

/** 기본 노출 캐리어. 가장 많이 찾는 사이즈. */
export const DEFAULT_SIZE: CarrierSize = '24'

export function isCarrierSize(v: string | undefined): v is CarrierSize {
  return v === '20' || v === '24' || v === '28'
}

/** 상세 페이지 검수 체크리스트 — prototype 샘플 항목.
    화면에 표시하는 검수 항목 수는 모두 이 배열 길이를 따른다 */
export const INSPECTION_CHECKS = ['세척 완료', '외관 검수', '바퀴 검수', '손잡이 검수', '지퍼 검수']

/** 등급 안내 — Collection 하단 범례 */
export const GRADES = [
  { grade: 'A GRADE', desc: '사용감이 거의 없는 상태' },
  { grade: 'B GRADE', desc: '미세한 외관 흔적, 기능 검수는 A와 동일' },
]

/** 모든 모델 공통 사양 — prototype 샘플 값 */
export const COMMON_SPEC = [
  { k: '소재', v: '폴리카보네이트 하드 쉘' },
  { k: '잠금', v: 'TSA 다이얼 잠금' },
]

/** 상세 페이지 등급 설명 (Collection 범례와 문구가 다르다 — prototype 그대로) */
export const GRADE_SPEC = { A: '사용감이 거의 없는 상태', B: '미세한 외관 흔적, 기능 이상 없음' } as const

/** 상세 페이지 이용 방법 4단계 — 배송 방식은 prototype 기준, 파손 보장은 미정 */
export const HOW_TO_USE = [
  { t: '날짜 선택', s: '여행 일정에 맞춰 대여 기간을 고릅니다.' },
  { t: '문 앞 배송', s: '출발 전날까지 집 앞으로 도착합니다.' },
  { t: '여행', s: '파손 보장 정책은 준비 중입니다.' },
  { t: '문 앞 반납', s: '반납일에 문 앞에서 회수합니다. 세척은 저희가 합니다.' },
]
