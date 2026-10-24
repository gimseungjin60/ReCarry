/* 표시용 포맷 헬퍼. prototype 의 won / fmt / nights / priceFor 와 동일 규칙. */

import type { Carrier } from '@/data/types'

const DAYS = ['일', '월', '화', '수', '목', '금', '토']

export const won = (n: number) => n.toLocaleString('ko-KR') + '원'

/** "3월 22일 (일)" */
export const fmt = (d: Date) => `${d.getMonth() + 1}월 ${d.getDate()}일 (${DAYS[d.getDay()]})`

/** 선택한 날짜 범위의 숙박 수. 둘 중 하나라도 없으면 0. */
export const nights = (start: Date | null, end: Date | null) =>
  start && end ? Math.round((end.getTime() - start.getTime()) / 864e5) : 0

export const stayLabel = (n: number) => `${n}박 ${n + 1}일`

export const addDays = (d: Date, n: number) => {
  const x = new Date(d)
  x.setDate(x.getDate() + n)
  return x
}

/** 기본 2박까지는 기본 요금, 그 이상은 1박당 extra 가 붙는다. */
export const priceFor = (p: Carrier, n: number) => p.price + Math.max(0, (n || 2) - 2) * p.extra

/** API 날짜 'YYYY-MM-DD' (로컬 날짜 기준 — toISOString 은 UTC 라 하루 밀릴 수 있다) */
export const ymd = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/** 서비스 지역(Asia/Seoul)의 오늘 — 서버가 출발일을 판단하는 기준. 다른 시간대 브라우저에서도 같다 */
export const seoulToday = () => parseYmd(new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date()))

/** 한 번에 예약할 수 있는 최대 박수 (backend recarry.booking.max-nights) */
export const MAX_NIGHTS = 30

/** 휴대폰 번호 형식 — backend AuthDtos.PHONE 과 같다 (숫자 9개 이상) */
export const isPhone = (v: string) => /^(?=(?:[^0-9]*[0-9]){9})[0-9+\- ]{9,20}$/.test(v.trim())

/** 'YYYY-MM-DD' → 로컬 Date */
export const parseYmd = (s: string) => {
  const [y, m, d] = s.split('-').map(Number)
  return new Date(y, m - 1, d)
}

/** 서버 시각(ISO) → '2026. 10. 24. 오후 3:00' */
export const dateTime = (iso: string) => new Date(iso).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
