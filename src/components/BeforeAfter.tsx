import { useState } from 'react'
import { ProductBag } from './CarrierBag'
import type { Carrier } from '@/data/types'
import './BeforeAfter.css'

/* 회수 당시(before) ↔ 검수 완료(after) 비교. 투명 range input 을 드래그해 경계를 옮긴다. */

export default function BeforeAfter({ carrier }: { carrier: Carrier }) {
  const [v, setV] = useState(50)

  return (
    <div className="compare">
      <div className="layer before">
        <span className="lbl">BEFORE — {carrier.story[0].date}</span>
        <ProductBag carrier={carrier} worn handle="down" tag label={`${carrier.name} 회수 당시`} />
      </div>
      <div className="layer after" style={{ clipPath: `inset(0 0 0 ${v}%)` }}>
        <span className="lbl">AFTER — {carrier.inspectedAt}</span>
        <ProductBag carrier={carrier} handle="down" label={`${carrier.name} 검수 완료`} />
      </div>
      <div className="bar" style={{ left: `${v}%` }} />
      <div className="knob" style={{ left: `${v}%` }} aria-hidden="true">
        ‹ ›
      </div>
      <input
        type="range"
        min={0}
        max={100}
        value={v}
        onChange={(e) => setV(+e.target.value)}
        aria-label="회수 당시와 검수 완료 모습 비교"
      />
    </div>
  )
}
