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

/* ---------------------------------------------------------------- API
   backend 응답 형태 (backend/.../dto). 날짜는 'YYYY-MM-DD', 시각은 ISO 문자열. */

export type Role = 'USER' | 'ADMIN'

export interface User {
  id: number
  email: string
  name: string
  phone: string
  role: Role
  createdAt: string
}

/** 로그인·가입 응답. 토큰은 본문에 없다 — httpOnly 쿠키로만 온다 */
export interface AuthResponse {
  expiresAt: string
  user: User
}

/** 저장되는 운영 상태 + 예약에서 계산되는 RESERVED / RENTED */
export type CarrierStatus = 'AVAILABLE' | 'INSPECTION' | 'REPAIR' | 'UNAVAILABLE' | 'RESERVED' | 'RENTED'

export interface ApiCarrier {
  id: number
  code: string
  size: CarrierSize
  grade: Grade
  status: CarrierStatus
  collectedFrom: string
  repairSummary: string
  inspectedAt: string | null
  events: { date: string; type: string; title: string; detail: string }[]
}

export interface ApiCarrierModel {
  size: CarrierSize
  name: string
  inch: string
  capacity: string
  usage: string
  dims: string
  weight: string
  price: number
  extraNightPrice: number
  headline: string[]
  description: string
  availableCount: number
  featured: ApiCarrier
}

export interface Availability {
  size: CarrierSize
  available: number
  nights: number
  totalPrice: number
}

export type BookingStatus = 'REQUESTED' | 'CONFIRMED' | 'IN_USE' | 'RETURNED' | 'CANCELLED'

export interface Booking {
  bookingNumber: string
  status: BookingStatus
  size: CarrierSize
  carrierName: string
  carrierCode: string
  grade: Grade
  startDate: string
  endDate: string
  nights: number
  deliveryDate: string
  pickupDate: string
  recipientName: string
  phone: string
  address: string
  addressDetail: string
  requestMessage: string
  basePrice: number
  extraPrice: number
  totalPrice: number
  cancellable: boolean
  createdAt: string
  cancelledAt: string | null
  /** 관리자 응답에만 있다 */
  customer: { id: number; email: string; name: string } | null
}
