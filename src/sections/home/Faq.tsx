import { Link } from 'react-router-dom'
import SectionHeader from '@/components/SectionHeader'
import { FAQ } from '@/data/home'

/* 자주 묻는 질문 — 대여 정책(/policy)의 핵심만. 정확한 기준은 정책 문서가 우선한다. */

export default function Faq() {
  return (
    <section className="sec" id="faq">
      <div className="wrap grid faq">
        <div className="faq-l" data-reveal>
          <SectionHeader index="04" eyebrow="FAQ" title={['빌리기 전에', '궁금한 것들.']} />
          <p className="small" style={{ marginTop: 20 }}>
            자세한 기준은{' '}
            <Link className="link" to="/policy">
              대여 정책
            </Link>
            에 있습니다.
          </p>
        </div>
        <div className="faq-r" data-reveal>
          {FAQ.map((f) => (
            <details key={f.q}>
              <summary>{f.q}</summary>
              <p>{f.a}</p>
            </details>
          ))}
        </div>
      </div>
    </section>
  )
}
