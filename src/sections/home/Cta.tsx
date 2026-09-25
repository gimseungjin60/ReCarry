import Button from '@/components/Button'
import CarrierBag from '@/components/CarrierBag'
import { useAnchorNav } from '@/lib/useAnchorNav'

export default function Cta() {
  const goAnchor = useAnchorNav()

  return (
    <section className="sec blk-sand cta" id="cta">
      <div className="wrap grid">
        <div className="cta-copy" data-reveal>
          <p className="d-l pre">
            다음 여행을 위해
            <br />새 캐리어를 구매하는 대신
          </p>
          <p className="d-l" style={{ marginTop: 12 }}>
            다시 여행하는 캐리어를
            <br />
            선택하세요.
          </p>
          <Button arrow onClick={() => goAnchor('collection')}>
            RECARRY 시작하기
          </Button>
        </div>
        <div className="cta-art" data-reveal>
          <CarrierBag color="chalk" width={150} height={236} tag />
        </div>
      </div>
    </section>
  )
}
