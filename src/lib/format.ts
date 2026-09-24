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
