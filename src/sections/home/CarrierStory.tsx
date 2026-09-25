import SectionHeader from '@/components/SectionHeader'
import BeforeAfter from '@/components/BeforeAfter'
import CarrierPassport from '@/components/CarrierPassport'
import CarrierTimeline from '@/components/CarrierTimeline'
import { DEFAULT_SIZE } from '@/data/carriers'
import { useCatalog } from '@/lib/catalog'
import { HOME_IMAGES } from '@/data/home'

export default function CarrierStory() {
  const c = useCatalog().bySize(DEFAULT_SIZE)
  // 타임라인이 비어 있는 캐리어도 있을 수 있다
  const year = c?.story[0]?.date.slice(0, 4)
  const trip = c?.story.at(-1)

  return (
    <section className="sec blk-night" id="story">
      <div className="wrap">
        <SectionHeader index="04" eyebrow="The story of a carrier" />
        <div className="grid story-head" style={{ marginTop: 40 }} data-reveal>
          <div className="l">
            <h2 className="d-latin">
              From airport
              <br />
              to another
              <br />
              journey
            </h2>
            <p className="story-line">
              누군가의 여행을 마친 캐리어가,
              <br />
              이제 당신의 손에서 다시 출발합니다.
            </p>
            <p className="muted story-note">
              모든 RECARRY 캐리어에는 고유 ID가 있습니다. 어디서 왔고, 무엇을 고쳤고, 어떤 검수를 통과했는지
              기록하고 공개합니다.
            </p>
          </div>
          <figure className="story-fig" data-reveal="image">
            <img
              className="ph"
              src={HOME_IMAGES.story.src}
              alt={HOME_IMAGES.story.alt}
              style={{ objectPosition: '50% 22%' }}
              decoding="async"
              loading="lazy"
            />
            <figcaption className="mono muted">
              {c && (trip && year ? `${c.id} — 2nd journey, ${year}.${trip.date}` : c.id)}
            </figcaption>
          </figure>
        </div>

        {c && (
          <div className="grid story-body" data-reveal>
            <BeforeAfter carrier={c} />
            <div className="passport">
              <CarrierPassport carrier={c} />
              <CarrierTimeline story={c.story} />
              <p className="small muted" style={{ marginTop: 20 }}>
                검수·수리 이력은 캐리어 ID별로 기록하고 관리합니다.
              </p>
            </div>
          </div>
        )}
      </div>
    </section>
  )
}
