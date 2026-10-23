import { Link } from 'react-router-dom'
import { CarrierMedia } from './CarrierBag'
import { Arrow } from './Button'
import { won } from '@/lib/format'
import type { Carrier } from '@/data/types'
import './ProductCard.css'

/* PRD §4 Collection 표시 정보: Size / Grade / Price / Capacity / Usage / Inspection status */

export default function ProductCard({ carrier }: { carrier: Carrier }) {
  return (
    <Link className="pcard" to={`/product/${carrier.key}`}>
      <div className="frame" style={{ background: carrier.bg }}>
        <CarrierMedia carrier={carrier} />
        <div className="corner">
          <span className="mono">{carrier.id}</span>
          <span className="tag">{carrier.grade} Grade</span>
        </div>
      </div>

      <div className="pcard-body">
        <div className="pcard-row">
          <h3 className="pcard-name">{carrier.name}</h3>
          <span className="pcard-price">
            {won(carrier.price)}
            <small>/ 2박 3일</small>
          </span>
        </div>

        <div className="pcard-meta">
          <span>{carrier.inch}</span>
          <span aria-hidden="true">·</span>
          <span>{carrier.capacity}</span>
          <span aria-hidden="true">·</span>
          <span>{carrier.usage}</span>
        </div>

        {/* 실제 검수 기록이 있을 때만 — 모든 캐리어에 같은 문구를 붙이지 않는다 */}
        <div className="pcard-checks">
          <span>1박 추가 {won(carrier.extra)}</span>
          {carrier.inspection.length > 0 && (
            <span>
              <i className="dot" />
              검수 {carrier.inspection.filter((c) => c.passed).length}/{carrier.inspection.length} 통과
            </span>
          )}
        </div>

        <span className="link-arrow">
          자세히 보기 <Arrow />
        </span>
      </div>
    </Link>
  )
}
