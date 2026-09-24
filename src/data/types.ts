/* RECARRY 도메인 타입.
   구조는 prototype/recarry-prototype-v2.html 의 P / STEPS 객체를 따른다. */

export type CarrierSize = '20' | '24' | '28'
export type Grade = 'A' | 'B'

/** 일러스트 캐리어의 셸 색상 키. 실제 팔레트는 CarrierBag 에 있다. */
export type BagColor = 'graphite' | 'chalk' | 'clay'

/** Carrier Story 타임라인 한 줄: 날짜 / 무슨 일이 있었는지 / 상세 */
export interface StoryEntry {
  date: string
  title: string
  detail: string
}

export interface Carrier {
  key: CarrierSize
  name: string
  inch: string
  grade: Grade

  /** 2박 3일 기준 요금 (원) */
  price: number
  /** 1박 추가 요금 (원) */
  extra: number

  capacity: string
  usage: string
  dims: string
  weight: string

  /** Carrier ID — 캐리어 한 대를 개별 자산으로 식별한다 */
  id: string
  collectedFrom: string
  repair: string
  inspectedAt: string
  available: number

  /** 상세 페이지 헤드라인. 줄바꿈을 배열로 표현한다 (HTML 주입 회피) */
  headline: string[]
  desc: string
  story: StoryEntry[]

  /** 표시용 */
  color: BagColor
  bg: string
  photo: string | null
  photoPos: string
  bagWidth: number
  bagHeight: number
}

export interface ProcessStep {
  ko: string
  en: string
  desc: string
}
