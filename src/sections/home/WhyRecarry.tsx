import SectionHeader from '@/components/SectionHeader'
import CarrierBag from '@/components/CarrierBag'
import { WHY_FRAMES } from '@/data/home'

export default function WhyRecarry() {
  return (
    <section className="sec" id="why">
      <div className="wrap">
        <div className="grid">
          <div className="why-head" data-reveal>
            {/* 모바일에서만 줄바꿈이 풀리는 br-d 가 있어 title 배열 대신 직접 쓴다 */}
            <SectionHeader index="01" eyebrow="Why RECARRY">
              <h2 className="d-l" style={{ marginTop: 32 }}>
                아직 여행할 수 있는 캐리어가 <br className="br-d" />왜 버려져야 할까요?
              </h2>
            </SectionHeader>
          </div>
          <p className="why-note muted" data-reveal>
            바퀴 하나, 손잡이 하나의 고장으로 많은 캐리어가 버려집니다. 대부분은 수리와 세척만으로 다시 여행할 수
            있습니다.
          </p>
        </div>

        <div className="why-frames" data-reveal="stagger">
          {WHY_FRAMES.map((f, i) => (
            <figure key={f.title}>
              <div className="frame" style={{ background: f.bg }}>
                <CarrierBag color="chalk" width={150} height={236} {...f.bag} />
              </div>
              <figcaption>
                <span className="mono muted">0{i + 1}</span>
                <p className="t">{f.title}</p>
                <p className="small">{f.desc}</p>
              </figcaption>
            </figure>
          ))}
        </div>
        <p className="why-rail label">One carrier · Four moments</p>
      </div>
    </section>
  )
}
