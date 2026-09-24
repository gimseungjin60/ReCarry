import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import Button from '@/components/Button'
import SectionHeader from '@/components/SectionHeader'
import CarrierPassport from '@/components/CarrierPassport'
import { CarrierMedia } from '@/components/CarrierBag'
import { CARRIERS, isCarrierSize } from '@/data/carriers'
import { won } from '@/lib/format'

/* PRD §4 Product Detail.
   갤러리 썸네일 / before-after / 타임라인 / 캘린더는 Step 2·3 에서 붙인다. */

export default function Product() {
  const { size } = useParams()
  const navigate = useNavigate()

  // 없는 사이즈로 들어오면 조용히 컬렉션으로 되돌린다
  if (!isCarrierSize(size)) {
    return <Navigate to="/" state={{ anchor: 'collection' }} replace />
  }

  const p = CARRIERS[size]
  const book = () => navigate('/booking', { state: { size: p.key } })

  return (
    <>
      <div className="wrap">
        <div className="crumb">
          <Link to="/" state={{ anchor: 'collection' }}>
            Collection
          </Link>
          <span>/</span>
          <span style={{ color: 'var(--ink)' }}>{p.name}</span>
        </div>

        <div className="grid pd">
          <div className="pd-gallery">
            <div className="frame pd-main" style={{ background: p.bg }}>
              <CarrierMedia carrier={p} tag />
              <div className="corner">
                <span className="mono">{p.id}</span>
                <span className="tag">{p.grade} Grade</span>
              </div>
            </div>
          </div>

          <aside className="pd-panel">
            <p className="mono muted">{p.id} · 이용 가능</p>
            <h1 className="d-m">{p.name}</h1>

            <div className="pd-meta">
              <span style={{ fontSize: 15 }}>{p.inch}</span>
              <span className="tag">{p.grade} Grade</span>
            </div>
            <p className="small" style={{ marginTop: 12 }}>
              {p.usage} · {p.capacity}
            </p>

            <div className="pd-price">
              <span className="p">{won(p.price)}</span>
              <span className="small">
                / 2박 3일 · 1박 추가 {won(p.extra)}
              </span>
            </div>

            <div style={{ marginTop: 24 }}>
              <Button block arrow onClick={book}>
                예약하기
              </Button>
            </div>
            <p className="small" style={{ marginTop: 12 }}>
              출발 전날 문 앞 배송 · 반납일 문 앞 회수 (무료)
            </p>
            <p className="mono muted" style={{ marginTop: 24 }}>
              INSPECTED {p.inspectedAt} · 12 / 12 CHECKS
            </p>
          </aside>
        </div>

        <section className="pd-sec">
          <div className="grid">
            <div style={{ gridColumn: '1/6' }}>
              <SectionHeader eyebrow="Product" title={p.headline} size="d-m" />
              <p className="muted" style={{ marginTop: 20, maxWidth: 420 }}>
                {p.desc}
              </p>
            </div>
          </div>
        </section>

        <section className="pd-sec" style={{ paddingBottom: 'var(--space-10)' }}>
          <div className="grid">
            <div style={{ gridColumn: '1/6' }}>
              <SectionHeader
                eyebrow="The story of this carrier"
                title={[`${p.collectedFrom}에서`, '다시 여행으로.']}
                size="d-m"
              />
            </div>
            <div className="blk-night" style={{ gridColumn: '7/13' }}>
              <CarrierPassport carrier={p} />
            </div>
          </div>
        </section>
      </div>

      <div className="pd-bar">
        <div>
          <div className="p">{won(p.price)}</div>
          <div className="small">2박 3일 기준</div>
        </div>
        <Button small onClick={book}>
          예약하기
        </Button>
      </div>
    </>
  )
}
