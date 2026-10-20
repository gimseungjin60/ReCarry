import { Link } from 'react-router-dom'
import { LEGAL, LEGAL_EFFECTIVE } from '@/data/legal'
import type { LegalKey } from '@/data/legal'

/* 이용약관 · 개인정보 처리방침 · 대여 정책. 내용은 data/legal.ts 한 곳에 있다. */

export default function Legal({ doc }: { doc: LegalKey }) {
  const d = LEGAL[doc]
  return (
    <div className="wrap legal-pg">
      <p className="label muted">{d.en}</p>
      <h1 className="d-m" style={{ marginTop: 12 }}>
        {d.title}
      </h1>
      <p className="mono muted" style={{ marginTop: 12 }}>
        시행일 {LEGAL_EFFECTIVE}
      </p>
      <div className="note" style={{ marginTop: 24 }}>
        <span className="mono">DRAFT</span>
        <span>정식 서비스 전 초안입니다. [확정 필요] 항목과 법률 검토를 거쳐 확정됩니다.</span>
      </div>
      <nav className="legal-nav" aria-label="정책 문서">
        {(Object.keys(LEGAL) as LegalKey[]).map((k) => (
          <Link key={k} to={LEGAL[k].path} aria-current={k === doc ? 'page' : undefined}>
            {LEGAL[k].title}
          </Link>
        ))}
      </nav>
      {d.sections.map((s) => (
        <section key={s.title} className="legal-sec">
          <h2 className="t-title">{s.title}</h2>
          {s.body?.map((p) => <p key={p}>{p}</p>)}
          {s.list && (
            <ul>
              {s.list.map((li) => (
                <li key={li}>{li}</li>
              ))}
            </ul>
          )}
        </section>
      ))}
    </div>
  )
}
