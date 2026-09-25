import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Button from '@/components/Button'
import BookingTicket from '@/components/BookingTicket'
import { BOOKING_STATUS } from '@/data/status'
import type { Booking } from '@/data/types'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { fmt, parseYmd, won } from '@/lib/format'

/* PRD 확장 — 최소 My Page. 회원 정보 · 예약 목록 · 예약 상세(취소). */

export function MyPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [list, setList] = useState<Booking[] | null>(null)
  const [err, setErr] = useState('')

  useEffect(() => {
    api<Booking[]>('/api/bookings')
      .then(setList)
      .catch((e: ApiError) => setErr(e.message))
  }, [])

  if (!user) return null

  return (
    <div className="wrap acct-pg">
      <p className="label muted">My page</p>
      <h1 className="d-m" style={{ marginTop: 12 }}>
        {user.name}님의 여행
      </h1>

      <div className="grid acct">
        <section className="acct-main" aria-labelledby="my-bookings">
          <h2 className="t-title" id="my-bookings">
            예약 내역
          </h2>
          {err && (
            <p className="err" role="alert">
              {err}
            </p>
          )}
          {list && list.length === 0 && (
            <div className="note" style={{ marginTop: 20 }}>
              <span>아직 예약이 없습니다.</span>
              <Link className="link" to="/" state={{ anchor: 'collection' }}>
                캐리어 둘러보기
              </Link>
            </div>
          )}
          {list && list.length > 0 && (
            <ul className="rows">
              {list.map((b) => (
                <li key={b.bookingNumber}>
                  <Link to={`/mypage/bookings/${b.bookingNumber}`}>
                    <span className="mono muted">{b.bookingNumber}</span>
                    <span className="nm">
                      {b.carrierName} <span className="mono muted">{b.carrierCode}</span>
                    </span>
                    <span className="small">
                      {fmt(parseYmd(b.startDate))} — {fmt(parseYmd(b.endDate))}
                    </span>
                    <span className="st">
                      <span className="tag">{BOOKING_STATUS[b.status]}</span>
                      {won(b.totalPrice)}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        <aside className="acct-side" aria-label="회원 정보">
          <p className="label muted">Account</p>
          <dl className="sum">
            <div>
              <dt>이름</dt>
              <dd>{user.name}</dd>
            </div>
            <div>
              <dt>이메일</dt>
              <dd>{user.email}</dd>
            </div>
            <div>
              <dt>휴대폰</dt>
              <dd>{user.phone || '—'}</dd>
            </div>
          </dl>
          <div style={{ marginTop: 24, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            {user.role === 'ADMIN' && (
              <Button to="/admin" variant="secondary" small>
                관리자
              </Button>
            )}
            <Button
              variant="secondary"
              small
              onClick={() => {
                // 보호된 화면을 먼저 떠난다 — 반대 순서면 로그인 화면으로 튕긴다
                navigate('/', { replace: true })
                logout()
              }}
            >
              로그아웃
            </Button>
          </div>
        </aside>
      </div>
    </div>
  )
}

export function MyBooking() {
  const { bookingNumber } = useParams()
  const [b, setB] = useState<Booking | null>(null)
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api<Booking>(`/api/bookings/${encodeURIComponent(bookingNumber ?? '')}`)
      .then(setB)
      .catch((e: ApiError) => setErr(e.message))
  }, [bookingNumber])

  const cancel = async () => {
    if (!b) return
    setBusy(true)
    setErr('')
    try {
      setB(await api<Booking>(`/api/bookings/${b.bookingNumber}/cancel`, { method: 'POST' }))
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : '취소하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="wrap acct-pg">
      <div className="crumb" style={{ padding: 0 }}>
        <Link to="/mypage">My page</Link>
        <span>/</span>
        <span style={{ color: 'var(--ink)' }}>예약 상세</span>
      </div>
      {err && !b && (
        <p className="note" role="alert" style={{ marginTop: 32 }}>
          {err}
        </p>
      )}
      {b && (
        <>
          <BookingTicket booking={b} full />
          <p className="err" role="alert">
            {err}
          </p>
          {b.cancellable && (
            <div style={{ marginTop: 24 }}>
              {/* 취소·환불 정책은 미정 — 결제 전(예약 요청) 단계만 취소할 수 있다 */}
              <Button variant="secondary" onClick={cancel} disabled={busy}>
                {busy ? '취소 중…' : '예약 요청 취소'}
              </Button>
              <p className="small" style={{ marginTop: 12 }}>
                예약 요청 단계에서만 직접 취소할 수 있습니다. 취소·환불 정책은 준비 중입니다.
              </p>
            </div>
          )}
        </>
      )}
    </div>
  )
}
