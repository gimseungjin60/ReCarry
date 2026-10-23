import Button from '@/components/Button'
import CarrierBag from '@/components/CarrierBag'
import { useAnchorNav } from '@/lib/useAnchorNav'

/* 마지막 CTA — 브랜드 블루 띠. 첫 화면의 예약 바로 돌아간다 */

export default function Cta() {
  const goAnchor = useAnchorNav()

  return (
    <section className="sec cta" id="cta">
      <div className="wrap grid">
        <div className="cta-copy" data-reveal>
          <p className="label">Second journey</p>
          <p className="d-l">
            다음 여행은,
            <br />
            다시 여행하는 캐리어로.
          </p>
          <Button variant="light" arrow onClick={() => goAnchor('top')}>
            날짜 고르고 예약하기
          </Button>
        </div>
        <div className="cta-art" data-reveal>
          <CarrierBag color="graphite" width={150} height={236} tag />
        </div>
      </div>
    </section>
  )
}
