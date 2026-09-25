/* 순환 과정 / 예약 단계 정의.
   공정 이름과 항목 수는 prototype 기준의 샘플 값이다. (CLAUDE.md §5) */

import { INSPECTION_CHECKS } from './carriers'
import type { ProcessStep } from './types'

/** Circular Journey — 회수부터 다시 여행까지 6단계 */
export const PROCESS_STEPS: ProcessStep[] = [
  { ko: '회수', en: 'Collect', desc: '공항·호텔·가정에서 버려질 캐리어를 수거합니다.' },
  { ko: '세척', en: 'Clean', desc: '내부 원단과 외부 쉘을 세척하고 살균·탈취합니다.' },
  { ko: '수리', en: 'Repair', desc: '바퀴, 손잡이, 지퍼 같은 소모 부품을 교체합니다.' },
  { ko: '검수', en: 'Inspect', desc: `${INSPECTION_CHECKS.length}개 항목을 점검하고 A·B 등급을 매깁니다.` },
  { ko: '렌탈', en: 'Rent', desc: '필요한 날짜만큼, 문 앞까지 배송합니다.' },
  { ko: '다시 여행', en: 'Travel again', desc: '반납된 캐리어는 다시 처음으로. 순환은 멈추지 않습니다.' },
]

/** 예약 4단계 (PRD §4 Booking) */
export const BOOKING_STEPS = ['여행 날짜', '캐리어 선택', '배송 정보', '예약 확인'] as const
export type BookingStep = 1 | 2 | 3 | 4
