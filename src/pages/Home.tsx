import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import Button from '@/components/Button'
import SectionHeader from '@/components/SectionHeader'
import ProductCard from '@/components/ProductCard'
import CarrierPassport from '@/components/CarrierPassport'
import CarrierBag from '@/components/CarrierBag'
import { CARRIERS, CARRIER_LIST, DEFAULT_SIZE, GRADES } from '@/data/carriers'
import { PROCESS_STEPS } from '@/data/process'
import { useAnchorNav } from '@/lib/useAnchorNav'

/* PRD §4 Home — 7개 섹션의 순서와 id 를 고정한다.
   Hero 의 full-bleed 사진/애니메이션과 각 섹션 내부 비주얼(process ring,
   before-after, why frames …)은 Step 2 에서 채운다. */

export default function Home() {
  const { state } = useLocation()
  const goAnchor = useAnchorNav()
  const hero = CARRIERS[DEFAULT_SIZE]

  // 다른 페이지에서 앵커를 눌러 들어온 경우 해당 섹션으로 이동한다
  useEffect(() => {
    const anchor = (state as { anchor?: string } | null)?.anchor
    if (!anchor) return
    document.getElementById(anchor)?.scrollIntoView()
    // 새로고침·뒤로가기 때 다시 점프하지 않도록 state 를 비운다
    window.history.replaceState({}, '')
  }, [state])

  return (
    <>
      <section className="sec" id="hero" aria-label="RECARRY 소개">
        <div className="wrap">
          <p className="label idx">Circular carrier rental — Seoul</p>
          <h1 className="d-xl" style={{ marginTop: 32 }}>
            버려지는 캐리어를
            <br />
            다시 여행으로.
          </h1>
          <p className="lead" style={{ marginTop: 32, maxWidth: 400 }}>
            사용되지 않고 버려질 캐리어를
            <br />
            새로운 여행의 시작으로 바꿉니다.
          </p>
          <div style={{ display: 'flex', gap: 24, alignItems: 'center', marginTop: 48, flexWrap: 'wrap' }}>
            <Button arrow onClick={() => goAnchor('collection')}>
              캐리어 둘러보기
            </Button>
            <button className="link-arrow" onClick={() => goAnchor('how')}>
              순환 과정 보기 <span className="arr">→</span>
            </button>
          </div>
          <p className="mono muted" style={{ marginTop: 64 }}>
            {PROCESS_STEPS.map((s) => s.ko).join(' → ')}
          </p>
        </div>
      </section>

      <section className="sec" id="why">
        <div className="wrap">
          <div className="grid">
            <div style={{ gridColumn: '1/9' }}>
              <SectionHeader
                index="01"
                eyebrow="Why RECARRY"
                title={['아직 여행할 수 있는 캐리어가', '왜 버려져야 할까요?']}
              />
            </div>
            <p className="muted" style={{ gridColumn: '9/13', alignSelf: 'end', maxWidth: 340 }}>
              바퀴 하나, 손잡이 하나의 고장으로 많은 캐리어가 버려집니다. 대부분은 수리와 세척만으로 다시 여행할 수
              있습니다.
            </p>
          </div>
        </div>
      </section>

      <section className="sec blk-forest" id="how">
        <div className="wrap">
          <div className="grid">
            <div style={{ gridColumn: '1/8' }}>
              <SectionHeader
                index="02"
                eyebrow="How it works"
                title={['하나의 여행이 끝나면,', '다음 여행이 시작됩니다.']}
              />
              <p className="muted" style={{ marginTop: 24, maxWidth: 420 }}>
                모든 RECARRY 캐리어는 같은 순환을 거칩니다. 반납된 캐리어는 다시 처음으로 돌아갑니다.
              </p>
            </div>
          </div>
        </div>
      </section>

      <section className="sec" id="collection">
        <div className="wrap">
          <div className="grid" style={{ alignItems: 'end' }}>
            <div style={{ gridColumn: '1/8' }}>
              <SectionHeader index="03" eyebrow="Collection" title={['세 가지 사이즈,', '하나의 검수 기준.']} />
            </div>
            <p className="muted" style={{ gridColumn: '9/13' }}>
              모든 캐리어는 세척과 12개 항목 검수를 마친 뒤에만 컬렉션에 올라옵니다. 가격은 2박 3일 기준입니다.
            </p>
          </div>

          <div className="cards">
            {CARRIER_LIST.map((c) => (
              <ProductCard key={c.key} carrier={c} />
            ))}
          </div>

          <div className="grades">
            {GRADES.map((g) => (
              <span key={g.grade}>
                <b>{g.grade}</b>
                {g.desc}
              </span>
            ))}
          </div>
        </div>
      </section>

      <section className="sec blk-night" id="story">
        <div className="wrap">
          <SectionHeader index="04" eyebrow="The story of a carrier" />
          <div className="grid" style={{ marginTop: 40, alignItems: 'end' }}>
            <div style={{ gridColumn: '1/8' }}>
              <h2 className="d-latin">
                From airport
                <br />
                to another
                <br />
                journey
              </h2>
              <p className="muted" style={{ marginTop: 48, maxWidth: 440 }}>
                모든 RECARRY 캐리어에는 고유 ID가 있습니다. 어디서 왔고, 무엇을 고쳤고, 어떤 검수를 통과했는지
                기록하고 공개합니다.
              </p>
            </div>
            <div style={{ gridColumn: '9/13' }}>
              <CarrierPassport carrier={hero} />
            </div>
          </div>
        </div>
      </section>

      <section className="sec" id="rent">
        <div className="wrap">
          <div className="grid">
            <div style={{ gridColumn: '1/6' }}>
              <SectionHeader
                index="05"
                eyebrow="Why rent"
                title={['굳이 새로', '구매하지 않아도', '됩니다.']}
              />
              <p className="lead" style={{ marginTop: 28, maxWidth: 320 }}>
                필요한 기간만 사용하고
                <br />
                다시 돌려보내세요.
              </p>
            </div>
          </div>
        </div>
      </section>

      <section className="sec blk-sand" id="cta">
        <div className="wrap grid" style={{ alignItems: 'end' }}>
          <div style={{ gridColumn: '1/9' }}>
            <p className="d-l" style={{ color: 'var(--sand-ink)' }}>
              다음 여행을 위해
              <br className="br-d" />새 캐리어를 구매하는 대신
            </p>
            <p className="d-l" style={{ marginTop: 12 }}>
              다시 여행하는 캐리어를
              <br className="br-d" />
              선택하세요.
            </p>
            <div style={{ marginTop: 56 }}>
              <Button arrow onClick={() => goAnchor('collection')}>
                RECARRY 시작하기
              </Button>
            </div>
          </div>
          <div style={{ gridColumn: '9/13', display: 'flex', justifyContent: 'flex-end' }}>
            <CarrierBag color="chalk" width={150} height={236} tag style={{ height: 320 }} />
          </div>
        </div>
      </section>
    </>
  )
}
