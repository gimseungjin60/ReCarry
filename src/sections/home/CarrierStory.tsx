import SectionHeader from '@/components/SectionHeader'
import BeforeAfter from '@/components/BeforeAfter'
import CarrierPassport from '@/components/CarrierPassport'
import CarrierTimeline from '@/components/CarrierTimeline'
import { DEFAULT_SIZE } from '@/data/carriers'
import { useCatalog } from '@/lib/catalog'

/* Carrier ID · 검수 기록 — RECARRY 의 차별점. 대표 캐리어 한 대의 실제 기록(DB)을 보여준다. */

export default function CarrierStory() {
  const c = useCatalog().bySize(DEFAULT_SIZE)

  return (
    <section className="sec blk-night" id="story">
      <div className="wrap">
        <div className="grid story-head" data-reveal>
          <div className="l">
            <SectionHeader index="03" eyebrow="Carrier ID" title={['두 번째 여행을', '준비한 기록.']} />
          </div>
          <p className="muted story-note">
            모든 RECARRY 캐리어에는 고유 ID 가 있습니다. 언제 들어왔고, 무엇을 고쳤고, 어떤 검수를 통과했는지 한 대씩 기록하고
            공개합니다.
          </p>
        </div>

        {c && (
          <div className="grid story-body" data-reveal>
            <BeforeAfter carrier={c} />
            <div className="passport">
              <CarrierPassport carrier={c} />
              <CarrierTimeline story={c.story} />
            </div>
          </div>
        )}
      </div>
    </section>
  )
}
