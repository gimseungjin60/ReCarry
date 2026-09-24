/* ============================================================
   PROTOTYPE / SAMPLE DATA — 실제 운영 데이터가 아니다.
   가격, 추가 요금, 배송·회수 무료 여부, 회수처, 수리 이력,
   검수 항목 수, 등급, 재고(available) 는 모두 웹 MVP 검증용
   샘플 값이며 확정된 사업 정보가 아니다.
   실제 값이 확정되면 이 파일만 교체한다. (CLAUDE.md §5)
   ============================================================ */

import type { Carrier, CarrierSize } from './types'

export const CARRIERS: Record<CarrierSize, Carrier> = {
  '20': {
    key: '20',
    name: 'RECARRY 20"',
    inch: '20-inch',
    grade: 'A',
    price: 19900,
    extra: 4000,
    capacity: '38L',
    usage: '기내 반입 · 1–3박',
    dims: '36 × 23 × 55 cm',
    weight: '2.9 kg',
    id: 'RC-20-0412',
    collectedFrom: '김포공항 국내선',
    repair: '지퍼 슬라이더 교체',
    inspectedAt: '2026.04.05',
    available: 3,
    headline: ['기내에 그대로,', '짧은 여행을 위한 크기.'],
    desc: '국내선·단거리 해외 기내 반입 규격. 가볍고 단단한 폴리카보네이트 쉘에, 교체한 지퍼와 새 바퀴로 처음처럼 부드럽게 움직입니다.',
    story: [
      { date: '2026.04.02', title: '김포공항 국내선', detail: '폐기 예정 물품으로 분류된 캐리어를 인수' },
      { date: '04.03', title: '점검 및 수리', detail: '지퍼 슬라이더 교체, 바퀴 베어링 윤활' },
      { date: '04.04', title: '세척', detail: '내부 원단 세척, 외부 쉘 광택, 살균·탈취' },
      { date: '04.05', title: '검수 완료', detail: '12개 항목 통과 · A GRADE' },
      { date: '04.09', title: '새로운 여행', detail: '서울 → 오사카, 2박 3일' },
    ],
    color: 'chalk',
    bg: 'var(--stone)',
    photo: null,
    photoPos: '50% 50%',
    bagWidth: 132,
    bagHeight: 206,
  },
  '24': {
    key: '24',
    name: 'RECARRY 24"',
    inch: '24-inch',
    grade: 'A',
    price: 25900,
    extra: 5000,
    capacity: '64L',
    usage: '위탁 수하물 · 3–5박',
    dims: '44 × 27 × 66 cm',
    weight: '3.8 kg',
    id: 'RC-24-0187',
    collectedFrom: '인천공항 T1',
    repair: '바퀴 ×2, 손잡이 레일',
    inspectedAt: '2026.03.18',
    available: 2,
    headline: ['가볍고 단단한,', '가장 많이 찾는 사이즈.'],
    desc: '3–5박 해외여행에 알맞은 위탁 수하물 사이즈. 인천공항에서 회수되어 바퀴 두 개와 손잡이 레일을 교체하고, 12개 항목 검수를 통과했습니다.',
    story: [
      { date: '2026.03.14', title: '인천공항 T1에서 회수', detail: '출국장 폐기 수거함 앞에서 인수' },
      { date: '03.16', title: '점검 및 수리', detail: '바퀴 2개 교체, 손잡이 레일 교정' },
      { date: '03.17', title: '세척', detail: '내부 원단 세척, 외부 쉘 광택, 살균·탈취' },
      { date: '03.18', title: '검수 완료', detail: '12개 항목 통과 · A GRADE' },
      { date: '03.22', title: '새로운 여행', detail: '서울 → 제주, 3박 4일' },
    ],
    color: 'graphite',
    bg: 'var(--sand)',
    photo: null,
    photoPos: '50% 50%',
    bagWidth: 160,
    bagHeight: 250,
  },
  '28': {
    key: '28',
    name: 'RECARRY 28"',
    inch: '28-inch',
    grade: 'B',
    price: 31900,
    extra: 6000,
    capacity: '96L',
    usage: '장기 여행 · 7박 이상',
    dims: '50 × 30 × 76 cm',
    weight: '4.6 kg',
    id: 'RC-28-0093',
    collectedFrom: '서울 호텔 보관 물품',
    repair: '바퀴 ×4, 모서리 가드',
    inspectedAt: '2026.02.25',
    available: 1,
    headline: ['긴 여행도', '한 번에.'],
    desc: '일주일 이상의 여행과 이사, 유학 준비에 알맞은 대형 사이즈. 외관에 미세한 흔적이 남아 B GRADE로 분류했지만, 기능 검수는 A와 같은 기준을 통과했습니다.',
    story: [
      { date: '2026.02.20', title: '서울 호텔 보관 물품', detail: '보관 기한이 끝난 캐리어를 기증받아 인수' },
      { date: '02.23', title: '점검 및 수리', detail: '바퀴 4개 전체 교체, 모서리 가드 교체' },
      { date: '02.24', title: '세척', detail: '내부 원단 세척, 외부 쉘 광택, 살균·탈취' },
      { date: '02.25', title: '검수 완료', detail: '12개 항목 통과 · 외관 흔적으로 B GRADE' },
      { date: '03.02', title: '새로운 여행', detail: '인천 → 리스본, 9박 10일' },
    ],
    color: 'clay',
    bg: 'var(--canvas)',
    photo: null,
    photoPos: '50% 50%',
    bagWidth: 178,
    bagHeight: 284,
  },
}

export const CARRIER_LIST = Object.values(CARRIERS)

/** 기본 노출 캐리어. 가장 많이 찾는 사이즈. */
export const DEFAULT_SIZE: CarrierSize = '24'

export function isCarrierSize(v: string | undefined): v is CarrierSize {
  return v === '20' || v === '24' || v === '28'
}

/** 등급 안내 — Collection 하단 범례 */
export const GRADES = [
  { grade: 'A GRADE', desc: '사용감이 거의 없는 상태' },
  { grade: 'B GRADE', desc: '미세한 외관 흔적, 기능 검수는 A와 동일' },
]
