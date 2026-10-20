import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { BOOKING_STATUS } from '@/data/status'
import type { Booking } from '@/data/types'
import { api, ApiError } from '@/lib/api'
import { addDays, fmt, parseYmd, ymd } from '@/lib/format'

/* 관리자 운영 보조 — 날짜별 배송·회수 일정 (예약 날짜에서 계산) · 최근 변경 기록.
   배송·회수 방식은 아직 정하지 않았다 — 여기서는 "이 날 보낼 것 / 받을 것" 목록만 보여준다. */

const errorText = (e: unknown) => (e instanceof ApiError ? e.message : '요청을 처리하지 못했습니다.')

// 배송: 예약 요청·확정 건이 출발 전날 도착. 회수: 확정·이용 중 건의 도착일
const SHIP = ['REQUESTED', 'CONFIRMED']
const PICKUP = ['CONFIRMED', 'IN_USE']

/** version: 같은 화면에서 예약 상태가 바뀌면 올라간다 — 그때 다시 불러온다 */
export function Schedule({ version }: { version: number }) {
  const [day, setDay] = useState(ymd(new Date()))
  const [list, setList] = useState<Booking[] | null>(null)
  const [err, setErr] = useState('')

  useEffect(() => {
    api<Booking[]>('/api/admin/bookings')
      .then(setList)
      .catch((e) => setErr(errorText(e)))
  }, [version])

  const ship = list?.filter((b) => b.deliveryDate === day && SHIP.includes(b.status)) ?? []
  const pickup = list?.filter((b) => b.pickupDate === day && PICKUP.includes(b.status)) ?? []
  const shift = (n: number) => setDay(ymd(addDays(parseYmd(day), n)))

  return (
    <section className="adm-sec" aria-labelledby="adm-schedule">
      <h2 className="t-title" id="adm-schedule">
        배송 · 회수 일정
      </h2>
      <p className="small">출발 전날 도착(배송) · 도착일 회수 기준입니다. 예약 요청 건은 확정 전이라 배송 목록에 "요청"으로 표시됩니다.</p>
      <div className="adm-actions" style={{ alignItems: 'center', marginTop: 12 }}>
        <button type="button" className="link small" onClick={() => shift(-1)}>
          ← 전날
        </button>
        <input type="date" className="adm-input" style={{ width: 'auto' }} value={day} onChange={(e) => e.target.value && setDay(e.target.value)} />
        <button type="button" className="link small" onClick={() => shift(1)}>
          다음날 →
        </button>
      </div>
      <p className="err" role="alert">
        {err}
      </p>
      <div className="stock">
        {(
          [
            ['배송', ship],
            ['회수', pickup],
          ] as const
        ).map(([label, rows]) => (
          <div key={label}>
            <p className="label muted">
              {label} · {fmt(parseYmd(day))}
            </p>
            <p className="n">{rows.length}건</p>
            <ul className="adm-list" style={{ borderTop: 0, marginTop: 0 }}>
              {rows.map((b) => (
                <li key={b.bookingNumber} style={{ flexWrap: 'wrap' }}>
                  <Link className="link mono" to={`/admin/bookings/${b.bookingNumber}`}>
                    {b.bookingNumber}
                  </Link>
                  <span className="mono">{b.carrierCode}</span>
                  <span>{b.recipientName}</span>
                  <span className="small">{BOOKING_STATUS[b.status]}</span>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </section>
  )
}

interface Audit {
  at: string
  admin: string
  action: string
  target: string
  detail: string
}

const ACTION: Record<string, string> = {
  CARRIER_CREATE: '캐리어 등록',
  CARRIER_UPDATE: '캐리어 수정',
  CARRIER_STATUS: '캐리어 상태',
  CARRIER_INSPECTION: '검수 기록',
  CARRIER_EVENT_ADD: 'Story 추가',
  CARRIER_EVENT_DELETE: 'Story 삭제',
  MODEL_UPDATE: '상품 수정',
  BOOKING_STATUS: '예약 상태',
}

export function AuditLog() {
  const [open, setOpen] = useState(false)
  const [logs, setLogs] = useState<Audit[] | null>(null)
  const [err, setErr] = useState('')

  useEffect(() => {
    if (!open) return
    api<Audit[]>('/api/admin/audit-logs')
      .then(setLogs)
      .catch((e) => setErr(errorText(e)))
  }, [open])

  return (
    <section className="adm-sec" aria-labelledby="adm-audit">
      <h2 className="t-title" id="adm-audit">
        변경 기록
      </h2>
      <p className="small">관리자가 바꾼 캐리어·예약·상품 정보 (최근 100건). 자동 만료 취소는 예약의 취소 사유로 남습니다.</p>
      <button type="button" className="link small" style={{ marginTop: 8 }} onClick={() => setOpen(!open)}>
        {open ? '닫기' : '기록 보기'}
      </button>
      <p className="err" role="alert">
        {err}
      </p>
      {open && logs && (
        <div className="tbl-wrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>시각</th>
                <th>관리자</th>
                <th>작업</th>
                <th>대상</th>
                <th>내용</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((l, i) => (
                <tr key={i}>
                  <td className="mono">{new Date(l.at).toLocaleString('ko-KR')}</td>
                  <td>{l.admin}</td>
                  <td>{ACTION[l.action] ?? l.action}</td>
                  <td className="mono">{l.target}</td>
                  <td style={{ whiteSpace: 'normal' }}>{l.detail}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {logs.length === 0 && <p className="small">기록이 없습니다.</p>}
        </div>
      )}
    </section>
  )
}
