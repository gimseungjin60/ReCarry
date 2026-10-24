import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import BookingTicket from '@/components/BookingTicket'
import Button from '@/components/Button'
import { CarrierCreate, ModelEditor } from '@/pages/AdminCarrier'
import { AuditLog, Schedule, TwoFactor } from '@/pages/AdminOps'
import { BOOKING_NEXT, BOOKING_STATUS, CARRIER_OPERATIONAL, CARRIER_STATUS } from '@/data/status'
import type { ApiCarrier, Booking, BookingStatus, CarrierSize, CarrierStatus } from '@/data/types'
import { api, ApiError } from '@/lib/api'
import { invalidateCatalog } from '@/lib/catalog'
import { won } from '@/lib/format'
import { useTitle } from '@/lib/useTitle'

/* 관리자 화면 — 재고 요약 · 캐리어 목록(→ 상세 · 등록) · 사이즈 상품 · 예약 목록 / 상태 변경 / 상세.
   RequireAuth admin 으로 감싸고, 서버도 /api/admin/** 를 ADMIN 만 통과시킨다. */

const SIZES: CarrierSize[] = ['20', '24', '28']

export function Admin() {
  useTitle('운영 관리')
  const [carriers, setCarriers] = useState<ApiCarrier[] | null>(null)
  const [bookings, setBookings] = useState<Booking[] | null>(null)
  const [filter, setFilter] = useState<BookingStatus | ''>('')
  const [version, setVersion] = useState(0)
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
      invalidateCatalog()
      await loadCarriers()
    } catch (e) {
      fail(e)
    }
  }

  const setBookingStatus = async (number: string, status: BookingStatus) => {
    // 예약 상태는 되돌릴 수 없다 (취소 · 반납은 끝 상태)
    if (!window.confirm(`${number} 예약을 '${BOOKING_STATUS[status]}' 상태로 바꿀까요? 되돌릴 수 없습니다.`)) return
    setErr('')
    try {
      await api(`/api/admin/bookings/${number}/status`, { method: 'PATCH', body: { status } })
      // 예약 상태가 바뀌면 캐리어의 오늘 상태(예약됨·대여 중)도 바뀔 수 있다
      await Promise.all([loadBookings(), loadCarriers()])
      setVersion((v) => v + 1)
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

      <Schedule version={version} />

      <section className="adm-sec" aria-labelledby="adm-carriers">
        <h2 className="t-title" id="adm-carriers">
          캐리어
        </h2>
        <p className="small">
          예약됨 · 대여 중은 오늘 날짜의 예약으로 계산됩니다. 운영 상태만 직접 바꿀 수 있습니다. Carrier ID 를 누르면 검수 결과와
          Story 기록을 관리할 수 있습니다.
        </p>
        <CarrierCreate />
        <div className="tbl-wrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>Carrier ID</th>
                <th>사이즈</th>
                <th>등급</th>
                <th>입고처</th>
                <th>검수일</th>
                <th>검수 결과</th>
                <th>오늘 상태</th>
                <th>운영 상태 변경</th>
              </tr>
            </thead>
            <tbody>
              {carriers?.map((c) => (
                <tr key={c.id}>
                  <td className="mono">
                    <Link className="link" to={`/admin/carriers/${c.code}`}>
                      {c.code}
                    </Link>
                    {c.featured && <span className="small"> · 대표</span>}
                  </td>
                  <td>{c.size}"</td>
                  <td>{c.grade}</td>
                  <td>{c.collectedFrom}</td>
                  <td className="mono">{c.inspectedAt ?? '—'}</td>
                  <td className="mono">
                    {c.inspection.length ? `${c.inspection.filter((x) => x.passed).length}/${c.inspection.length}` : '기록 없음'}
                  </td>
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

      <ModelEditor />

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
                    <td className="mono">
                      <Link className="link" to={`/admin/carriers/${b.carrierCode}`}>
                        {b.carrierCode}
                      </Link>
                    </td>
                    <td>
                      {/* 연도까지 — 몇 달 뒤 예약도 섞여 있다 */}
                      {b.startDate.replaceAll('-', '.')} — {b.endDate.slice(5).replaceAll('-', '.')}
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

      <AuditLog />

      <TwoFactor />
    </div>
  )
}

export function AdminBooking() {
  useTitle('예약 상세 (관리)')
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
      <div className="crumb keep">
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
              <dt>캐리어</dt>
              <dd>
                <Link className="link mono" to={`/admin/carriers/${b.carrierCode}`}>
                  {b.carrierCode}
                </Link>
              </dd>
            </div>
            <div>
              <dt>접수</dt>
              <dd className="mono">{new Date(b.createdAt).toLocaleString('ko-KR')}</dd>
            </div>
          </dl>
          {/* 상태가 바뀌면 입력값을 새로 채운다 */}
          <BookingMoney key={b.status + (b.refundedAt ?? '')} b={b} onChange={setB} />
        </>
      )}
    </div>
  )
}

/** 예약 상세의 돈 처리 — 입금 확인(→ 확정) · 취소 환불액 · 환불 송금 완료 · 반납 후 보증금 정산. 금액 검증은 서버가 한다 */
function BookingMoney({ b, onChange }: { b: Booking; onChange: (b: Booking) => void }) {
  const [amount, setAmount] = useState(String(b.amountDue))
  const [payer, setPayer] = useState(b.payment?.payer ?? '')
  const [refund, setRefund] = useState(String(b.paidAmount ?? 0))
  const [deduction, setDeduction] = useState('0')
  const [note, setNote] = useState('')
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)
  const path = `/api/admin/bookings/${b.bookingNumber}`

  const run = async (question: string, url: string, method: 'POST' | 'PATCH', body?: object) => {
    if (!window.confirm(question)) return
    setBusy(true)
    setErr('')
    try {
      onChange(await api<Booking>(url, { method, body }))
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : '요청을 처리하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const num = (s: string) => Number(s.replace(/[^0-9]/g, '') || 0)
  const box = { maxWidth: 560, marginTop: 32 }

  let body = null
  if (b.status === 'REQUESTED') {
    body = (
      <>
        <h2 className="t-title">입금 확인</h2>
        <p className="small" style={{ margin: '8px 0 20px' }}>
          통장에 들어온 금액이 결제 예정 금액({won(b.amountDue)})과 같을 때만 확정됩니다. 확정 메일이 고객에게 갑니다.
        </p>
        <div className="row2">
          <label className="field">
            <span className="l">입금액</span>
            <input value={amount} onChange={(e) => setAmount(e.target.value)} inputMode="numeric" />
          </label>
          <label className="field">
            <span className="l">입금자명</span>
            <input value={payer} onChange={(e) => setPayer(e.target.value)} />
          </label>
        </div>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <Button
            disabled={busy}
            onClick={() =>
              run(`${won(num(amount))} 입금을 확인하고 예약을 확정할까요?`, `${path}/payment`, 'POST', {
                amount: num(amount),
                payerName: payer,
              })
            }
          >
            입금 확인 · 예약 확정
          </Button>
          <Button
            variant="secondary"
            disabled={busy}
            onClick={() => run('예약 요청을 거절(취소)할까요? 되돌릴 수 없습니다.', `${path}/status`, 'PATCH', { status: 'CANCELLED' })}
          >
            요청 거절
          </Button>
        </div>
      </>
    )
  } else if (b.status === 'CONFIRMED' && b.paidAmount != null) {
    body = (
      <>
        <h2 className="t-title">운영자 취소 · 환불</h2>
        <p className="small" style={{ margin: '8px 0 20px' }}>
          회사 사정 취소는 전액 환불이 기본입니다 (대여 정책). 입금액 {won(b.paidAmount)} 을 넘을 수 없습니다.
        </p>
        <label className="field">
          <span className="l">환불 금액</span>
          <input value={refund} onChange={(e) => setRefund(e.target.value)} inputMode="numeric" />
        </label>
        <Button
          variant="secondary"
          disabled={busy}
          onClick={() =>
            run(`예약을 취소하고 ${won(num(refund))}을 환불할까요? 되돌릴 수 없습니다.`, `${path}/status`, 'PATCH', {
              status: 'CANCELLED',
              refundAmount: num(refund),
            })
          }
        >
          예약 취소
        </Button>
      </>
    )
  } else if (b.status === 'CANCELLED' && b.refundAmount != null && !b.refundedAt) {
    body = (
      <>
        <h2 className="t-title">환불 송금</h2>
        <p className="small" style={{ margin: '8px 0 20px' }}>
          {won(b.refundAmount)}을 고객 계좌로 보낸 뒤 눌러주세요. 고객에게 환불 완료 메일이 갑니다.
        </p>
        <div style={{ marginTop: 16 }}>
          <Button disabled={busy} onClick={() => run(`${won(b.refundAmount ?? 0)} 송금을 마쳤나요?`, `${path}/refund-done`, 'POST')}>
            환불 송금 완료
          </Button>
        </div>
      </>
    )
  } else if (b.status === 'RETURNED' && b.paidAt && !b.depositSettledAt) {
    body = (
      <>
        <h2 className="t-title">보증금 정산</h2>
        <p className="small" style={{ margin: '8px 0 20px' }}>
          반납 검수 결과로 공제할 금액을 적어주세요 (보증금 {won(b.deposit)} 이하, 공제하면 사유 필수).
        </p>
        <div className="row2">
          <label className="field">
            <span className="l">공제 금액</span>
            <input value={deduction} onChange={(e) => setDeduction(e.target.value)} inputMode="numeric" />
          </label>
          <label className="field">
            <span className="l">공제 사유</span>
            <input value={note} onChange={(e) => setNote(e.target.value)} placeholder="예: 바퀴 교체" />
          </label>
        </div>
        <Button
          disabled={busy}
          onClick={() =>
            run(
              `공제 ${won(num(deduction))} · 돌려드림 ${won(b.deposit - num(deduction))} 으로 정산할까요?`,
              `${path}/deposit`,
              'POST',
              { deduction: num(deduction), note },
            )
          }
        >
          보증금 정산
        </Button>
      </>
    )
  }
  if (!body) return null
  return (
    <section style={box}>
      {body}
      <p className="err" role="alert">
        {err}
      </p>
    </section>
  )
}
