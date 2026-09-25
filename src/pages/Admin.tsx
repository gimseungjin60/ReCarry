import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import BookingTicket from '@/components/BookingTicket'
import { BOOKING_NEXT, BOOKING_STATUS, CARRIER_OPERATIONAL, CARRIER_STATUS } from '@/data/status'
import type { ApiCarrier, Booking, BookingStatus, CarrierSize, CarrierStatus } from '@/data/types'
import { api, ApiError } from '@/lib/api'
import { fmt, parseYmd, won } from '@/lib/format'

/* 최소 관리자 화면 — 재고 요약 · 캐리어 상태 변경 · 예약 목록 / 상태 변경 / 상세.
   RequireAuth admin 으로 감싸고, 서버도 /api/admin/** 를 ADMIN 만 통과시킨다. */

const SIZES: CarrierSize[] = ['20', '24', '28']

export function Admin() {
  const [carriers, setCarriers] = useState<ApiCarrier[] | null>(null)
  const [bookings, setBookings] = useState<Booking[] | null>(null)
  const [filter, setFilter] = useState<BookingStatus | ''>('')
  const [err, setErr] = useState('')

  const fail = (e: unknown) => setErr(e instanceof ApiError ? e.message : '요청을 처리하지 못했습니다.')

  const loadCarriers = useCallback(() => api<ApiCarrier[]>('/api/admin/carriers').then(setCarriers).catch(fail), [])
  const loadBookings = useCallback(
    () => api<Booking[]>('/api/admin/bookings' + (filter ? `?status=${filter}` : '')).then(setBookings).catch(fail),
    [filter],
  )
  useEffect(() => {
    loadCarriers()
  }, [loadCarriers])
  useEffect(() => {
    loadBookings()
  }, [loadBookings])

  const setCarrierStatus = async (id: number, status: CarrierStatus) => {
    setErr('')
    try {
      await api(`/api/admin/carriers/${id}/status`, { method: 'PATCH', body: { status } })
      await loadCarriers()
    } catch (e) {
      fail(e)
    }
  }

  const setBookingStatus = async (number: string, status: BookingStatus) => {
    setErr('')
    try {
      await api(`/api/admin/bookings/${number}/status`, { method: 'PATCH', body: { status } })
      // 예약 상태가 바뀌면 캐리어의 오늘 상태(예약됨·대여 중)도 바뀔 수 있다
      await Promise.all([loadBookings(), loadCarriers()])
    } catch (e) {
      fail(e)
    }
  }

  return (
    <div className="wrap acct-pg">
      <p className="label muted">Admin</p>
      <h1 className="d-m" style={{ marginTop: 12 }}>
        운영 관리
      </h1>
      <p className="err" role="alert">
        {err}
      </p>

      <section className="adm-sec" aria-labelledby="adm-stock">
        <h2 className="t-title" id="adm-stock">
          재고
        </h2>
        <div className="stock">
          {SIZES.map((size) => {
            const units = carriers?.filter((c) => c.size === size) ?? []
            const count = (s: CarrierStatus) => units.filter((c) => c.status === s).length
            return (
              <div key={size}>
                <p className="label muted">{size}-inch</p>
                <p className="n">{units.length}대</p>
                <p className="small">
                  대여 가능 {count('AVAILABLE')} · 예약 {count('RESERVED')} · 대여 중 {count('RENTED')}
                  <br />
                  세척·검수 {count('INSPECTION')} · 수리 {count('REPAIR')} · 중지 {count('UNAVAILABLE')}
                </p>
              </div>
            )
          })}
        </div>
      </section>

      <section className="adm-sec" aria-labelledby="adm-carriers">
        <h2 className="t-title" id="adm-carriers">
          캐리어
        </h2>
        <p className="small">예약됨 · 대여 중은 오늘 날짜의 예약으로 계산됩니다. 운영 상태만 직접 바꿀 수 있습니다.</p>
        <div className="tbl-wrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>Carrier ID</th>
                <th>사이즈</th>
                <th>등급</th>
                <th>회수처</th>
                <th>검수일</th>
                <th>오늘 상태</th>
                <th>운영 상태 변경</th>
              </tr>
            </thead>
            <tbody>
              {carriers?.map((c) => (
                <tr key={c.id}>
                  <td className="mono">{c.code}</td>
                  <td>{c.size}"</td>
                  <td>{c.grade}</td>
                  <td>{c.collectedFrom}</td>
                  <td className="mono">{c.inspectedAt ?? '—'}</td>
                  <td>{CARRIER_STATUS[c.status]}</td>
                  <td>
                    <select
                      aria-label={`${c.code} 운영 상태`}
                      value={CARRIER_OPERATIONAL.includes(c.status) ? c.status : 'AVAILABLE'}
                      onChange={(e) => setCarrierStatus(c.id, e.target.value as CarrierStatus)}
                    >
                      {CARRIER_OPERATIONAL.map((s) => (
                        <option key={s} value={s}>
                          {CARRIER_STATUS[s]}
                        </option>
                      ))}
                    </select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="adm-sec" aria-labelledby="adm-bookings">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 16, flexWrap: 'wrap' }}>
          <h2 className="t-title" id="adm-bookings">
            예약
          </h2>
          <label className="small">
            상태{' '}
            <select value={filter} onChange={(e) => setFilter(e.target.value as BookingStatus | '')}>
              <option value="">전체</option>
              {(Object.keys(BOOKING_STATUS) as BookingStatus[]).map((s) => (
                <option key={s} value={s}>
                  {BOOKING_STATUS[s]}
                </option>
              ))}
            </select>
          </label>
        </div>
        {bookings?.length === 0 && <p className="small" style={{ marginTop: 16 }}>예약이 없습니다.</p>}
        {!!bookings?.length && (
          <div className="tbl-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th>예약번호</th>
                  <th>고객</th>
                  <th>캐리어</th>
                  <th>기간</th>
                  <th>합계</th>
                  <th>상태</th>
                  <th>변경</th>
                </tr>
              </thead>
              <tbody>
                {bookings.map((b) => (
                  <tr key={b.bookingNumber}>
                    <td className="mono">
                      <Link className="link" to={`/admin/bookings/${b.bookingNumber}`}>
                        {b.bookingNumber}
                      </Link>
                    </td>
                    <td>{b.customer?.name}</td>
                    <td className="mono">{b.carrierCode}</td>
                    <td>
                      {fmt(parseYmd(b.startDate))} — {fmt(parseYmd(b.endDate))}
                    </td>
                    <td>{won(b.totalPrice)}</td>
                    <td>{BOOKING_STATUS[b.status]}</td>
                    <td>
                      {BOOKING_NEXT[b.status].length ? (
                        <select
                          aria-label={`${b.bookingNumber} 상태 변경`}
                          value=""
                          onChange={(e) => setBookingStatus(b.bookingNumber, e.target.value as BookingStatus)}
                        >
                          <option value="" disabled>
                            선택
                          </option>
                          {BOOKING_NEXT[b.status].map((s) => (
                            <option key={s} value={s}>
                              {BOOKING_STATUS[s]}
                            </option>
                          ))}
                        </select>
                      ) : (
                        '—'
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}

export function AdminBooking() {
  const { bookingNumber } = useParams()
  const [b, setB] = useState<Booking | null>(null)
  const [err, setErr] = useState('')

  useEffect(() => {
    api<Booking>(`/api/admin/bookings/${encodeURIComponent(bookingNumber ?? '')}`)
      .then(setB)
      .catch((e: ApiError) => setErr(e.message))
  }, [bookingNumber])

  return (
    <div className="wrap acct-pg">
      <div className="crumb" style={{ padding: 0 }}>
        <Link to="/admin">Admin</Link>
        <span>/</span>
        <span style={{ color: 'var(--ink)' }}>예약 상세</span>
      </div>
      {err && (
        <p className="note" role="alert" style={{ marginTop: 32 }}>
          {err}
        </p>
      )}
      {b && (
        <>
          <BookingTicket booking={b} full />
          <dl className="sum" style={{ maxWidth: 560, marginTop: 24 }}>
            <div>
              <dt>고객</dt>
              <dd>
                {b.customer?.name} · {b.customer?.email}
              </dd>
            </div>
            <div>
              <dt>접수</dt>
              <dd className="mono">{new Date(b.createdAt).toLocaleString('ko-KR')}</dd>
            </div>
          </dl>
        </>
      )}
    </div>
  )
}
