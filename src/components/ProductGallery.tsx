import { useState } from 'react'
import { CarrierMedia, ProductBag } from './CarrierBag'
import type { CarrierBagProps } from './CarrierBag'
import type { Carrier } from '@/data/types'

/* prototype galleryView(): 0 전체(사진 있으면 사진) · 1 손잡이 내림 · 2 바퀴 크롭 · 3 태그 크롭.
   실사진이 생기면 carrier.photo 만 채우면 0번 뷰가 사진으로 바뀐다. */

const VIEWS: CarrierBagProps[] = [
  { tag: true },
  { handle: 'down' },
  { viewBox: (g) => `${g.x - 6} ${g.bot - 64} 110 110`, label: '바퀴 상세' },
  { tag: true, viewBox: (g) => `${g.cx - 10} ${g.top - 36} 110 110`, label: '손잡이와 RECARRY 태그 상세' },
]

function View({ carrier, i, thumb }: { carrier: Carrier; i: number; thumb?: boolean }) {
  const v = VIEWS[i]
  if (i === 0) return <CarrierMedia carrier={carrier} {...v} />
  // 크롭 뷰는 frame 을 꽉 채운다 (.crop)
  const className = v.viewBox ? (thumb ? 'crop' : 'bag crop') : 'bag'
  return <ProductBag carrier={carrier} {...v} className={className} />
}

export default function ProductGallery({ carrier }: { carrier: Carrier }) {
  const [active, setActive] = useState(0)

  return (
    <div className="pd-gallery">
      <div className="frame pd-main" style={{ background: carrier.bg }}>
        <View carrier={carrier} i={active} />
        <div className="corner">
          <span className="mono">{carrier.id}</span>
          <span className="tag">{carrier.grade} Grade</span>
        </div>
      </div>
      <div className="pd-thumbs">
        {VIEWS.map((_, i) => (
          <button
            key={i}
            className={i === active ? 'on' : undefined}
            style={{ background: carrier.bg }}
            aria-label={`이미지 ${i + 1}`}
            aria-pressed={i === active}
            onClick={() => setActive(i)}
          >
            <View carrier={carrier} i={i} thumb />
          </button>
        ))}
      </div>
    </div>
  )
}
