/* 예약·캐리어 상태의 화면 문구. 허용 전이는 backend(BookingStatus.canMoveTo)가 최종 판단한다. */

import type { BookingStatus, CarrierStatus } from './types'

export const BOOKING_STATUS: Record<BookingStatus, string> = {
  REQUESTED: '예약 요청',
  CONFIRMED: '예약 확정',
  IN_USE: '이용 중',
  RETURNED: '반납 완료',
  CANCELLED: '취소',
}

/** 관리자 화면에서 고를 수 있는 다음 상태 (backend 규칙과 같다) */
export const BOOKING_NEXT: Record<BookingStatus, BookingStatus[]> = {
  REQUESTED: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['IN_USE', 'CANCELLED'],
  IN_USE: ['RETURNED'],
  RETURNED: [],
  CANCELLED: [],
}

export const CARRIER_STATUS: Record<CarrierStatus, string> = {
  AVAILABLE: '대여 가능',
  INSPECTION: '세척·검수 중',
  REPAIR: '수리 중',
  UNAVAILABLE: '운영 중지',
  RESERVED: '예약됨',
  RENTED: '대여 중',
}

/** 관리자가 직접 지정할 수 있는 운영 상태 (RESERVED / RENTED 는 예약에서 계산된다) */
export const CARRIER_OPERATIONAL: CarrierStatus[] = ['AVAILABLE', 'INSPECTION', 'REPAIR', 'UNAVAILABLE']
