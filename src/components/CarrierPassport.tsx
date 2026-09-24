import type { Carrier } from '@/data/types'
import './CarrierPassport.css'

/* Carrier ID 를 개별 자산처럼 보여주는 여권 카드.
   Home 의 Carrier Story 섹션과 Product 상세에서 함께 쓴다. */

export default function CarrierPassport({ carrier }: { carrier: Carrier }) {
  return (
    <div className="ptag">
      <div className="stripe" />
      <p className="label">Carrier ID</p>
      <p className="id">{carrier.id}</p>

      <dl>
        <div>
          <dt>Collected</dt>
          <dd>{carrier.collectedFrom}</dd>
        </div>
        <div>
          <dt>Model</dt>
          <dd>{carrier.inch} · PC hard shell</dd>
        </div>
        <div>
          <dt>Repaired</dt>
          <dd>{carrier.repair}</dd>
        </div>
        <div>
          <dt>Grade</dt>
          <dd>{carrier.grade} GRADE · 12/12</dd>
        </div>
      </dl>
    </div>
  )
}
