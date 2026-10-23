import { Fragment } from 'react'
import SectionHeader from '@/components/SectionHeader'
import { PROCESS_STEPS } from '@/data/process'
import { HOW_STEPS } from '@/data/home'

/* 이용 방법 — 고객이 겪는 4단계. 회사의 순환 공정은 아래 한 줄로만 보여준다. */

export default function HowItWorks() {
  return (
    <section className="sec blk-canvas" id="how">
      <div className="wrap">
        <div data-reveal>
          <SectionHeader index="02" eyebrow="How it works" title={['고르고, 받고,', '여행하고, 돌려주세요.']} />
        </div>
        <ol className="how-steps" data-reveal="stagger">
          {HOW_STEPS.map((s, i) => (
            <li key={s.t}>
              <span className="mono n">0{i + 1}</span>
              <p className="t">{s.t}</p>
              <p className="small">{s.s}</p>
            </li>
          ))}
        </ol>
        <p className="how-loop small" data-reveal>
          <span>모든 캐리어가 거치는 순환</span>
          {PROCESS_STEPS.map((s, i) => (
            <Fragment key={s.ko}>
              {i > 0 && <span aria-hidden="true">→</span>}
              <b>{s.ko}</b>
            </Fragment>
          ))}
        </p>
      </div>
    </section>
  )
}
