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

/** 검수 항목 (backend InspectionCheck.Item) */
export type InspectionItem = 'CLEANING' | 'EXTERIOR' | 'WHEELS' | 'HANDLE' | 'ZIPPER'

export interface InspectionCheck {
  item: InspectionItem
  passed: boolean
  note: string
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
  /** 대표 캐리어의 항목별 검수 결과. 비어 있으면 아직 기록이 없다 */
  inspection: InspectionCheck[]
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
  /** 시행 중인 약관·개인정보 처리방침에 아직 동의하지 않음 — /consent 를 먼저 거친다 */
  consentRequired: boolean
  emailVerified: boolean
  /** 메일 인증을 해야 예약할 수 있음 (서버가 메일을 보낼 수 있는 환경에서만 true) */
  needsEmailVerification: boolean
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
  featured: boolean
  inspection: InspectionCheck[]
  events: CarrierEvent[]
}

export type CarrierEventType = 'COLLECTED' | 'REPAIR' | 'CLEANING' | 'INSPECTION' | 'TRIP'

export interface CarrierEvent {
  id: number
  date: string
  type: CarrierEventType
  title: string
  detail: string
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
  /** 보증금 (대여 요금과 별도) */
  deposit: number
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
  /** 보증금 — 결제 예정 금액은 totalPrice + deposit */
  deposit: number
  /** 결제(입금)할 금액 = 대여 요금 + 보증금 */
  amountDue: number
  /** 고객이 직접 취소할 수 있음 — 예약 요청, 또는 배송 전 확정 예약 */
  cancellable: boolean
  /** 입금한 예약을 지금 취소하면 돌려받는 금액 (대여 정책 — 서버 계산) */
  cancelRefund: number | null
  createdAt: string
  /** 예약 요청일 때만: 이 시각까지 확정되지 않으면 자동 취소 */
  expiresAt: string | null
  cancelledAt: string | null
  cancelReason: 'USER' | 'ADMIN' | 'EXPIRED' | 'REJECTED' | null
  /** 예약 요청(입금 대기)일 때만: 입금 안내 */
  payment: BankInfo | null
  paidAt: string | null
  paidAmount: number | null
  payerName: string | null
  /** 입금 후 취소된 예약의 환불 금액 · 송금 완료 시각 */
  refundAmount: number | null
  refundedAt: string | null
  /** 반납 후 보증금 정산 */
  depositDeduction: number | null
  depositNote: string | null
  depositSettledAt: string | null
  /** 관리자 응답에만 있다 */
  customer: { id: number; email: string; name: string } | null
}

/** 무통장 입금 안내. sample = 아직 실제 계좌가 아닌 임시 계좌 */
export interface BankInfo {
  bank: string
  account: string
  holder: string
  sample: boolean
  amount: number
  /** 입금자명 (받는 분 이름 + 예약번호 끝 4자리) */
  payer: string
  deadline: string | null
}
