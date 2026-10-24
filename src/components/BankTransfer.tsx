import type { BankInfo } from '@/data/types'
import { won } from '@/lib/format'

/* 무통장 입금 안내 (입금 기한은 바로 위 BookingTicket 에 있다) — 예약 요청(입금 대기) 상태에서 Complete · My Page 상세가 같이 쓴다.
   계좌는 서버 설정(BANK_*)에서 온다. 아직 설정 전이면 임시 계좌라고 크게 알린다. */

export default function BankTransfer({ pay }: { pay: BankInfo }) {
  const rows: [string, string][] = [
    ['입금 계좌', `${pay.bank} ${pay.account}`],
    ['예금주', pay.holder],
    ['입금 금액', won(pay.amount)],
    ['입금자명', pay.payer],
  ]

  return (
    <section className="bank" aria-labelledby="bank-title">
      <p className="label" id="bank-title">
        Bank transfer · 무통장 입금
      </p>
      <dl className="sum">
        {rows.map(([k, v]) => (
          <div key={k}>
            <dt>{k}</dt>
            <dd className={k === '입금 계좌' ? 'mono' : undefined}>{v}</dd>
          </div>
        ))}
      </dl>
      <p className="small" style={{ marginTop: 12 }}>
        입금자명과 금액이 같아야 빨리 확인됩니다. 입금이 확인되면 예약이 확정되고 메일로 알려드립니다.
      </p>
      {pay.sample && (
        <p className="note" role="note" style={{ marginTop: 12 }}>
          <span className="mono">SAMPLE</span>
          <span>지금은 임시 계좌입니다. 실제로 입금하지 마세요.</span>
        </p>
      )}
    </section>
  )
}
