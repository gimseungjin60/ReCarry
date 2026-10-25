import { Link } from 'react-router-dom'
import { FaqList } from '@/sections/home/Faq'
import { FAQ, HELP_FAQ } from '@/data/home'
import { BUSINESS_INFO, TBD } from '@/data/legal'
import { useTitle } from '@/lib/useTitle'

/* 고객센터 — 자주 찾는 메뉴 · 자주 묻는 질문 · 문의처. 연락처는 사업자 정보가 확정되면 legal.ts 한 곳에서 바뀐다. */

const QUICK = [
  { to: '/mypage', t: '예약 조회 · 취소', s: '예약 상태 · 입금 안내 · 환불 금액' },
  { to: '/guide#pay', t: '결제 안내', s: '무통장 입금 · 입금자명' },
  { to: '/policy', t: '대여 정책', s: '취소 · 환불 · 파손 · 분실' },
  { to: '/forgot', t: '계정 찾기', s: '비밀번호 재설정 · 이메일 찾기' },
]

export default function Help() {
  useTitle('고객센터')
  const contact = BUSINESS_INFO.find(([k]) => k === '고객센터')?.[1] ?? TBD

  return (
    <div className="wrap legal-pg">
      <p className="label muted">Help center</p>
      <h1 className="d-m" style={{ marginTop: 12 }}>
        고객센터
      </h1>

      <ul className="quick">
        {QUICK.map((q) => (
          <li key={q.to}>
            <Link to={q.to}>
              <b>{q.t}</b>
              <span className="small">{q.s}</span>
            </Link>
          </li>
        ))}
      </ul>

      <section className="legal-sec faq">
        <h2 className="t-title">자주 묻는 질문</h2>
        <div style={{ marginTop: 12, borderTop: '1px solid var(--ink)' }}>
          <FaqList items={[...FAQ, ...HELP_FAQ]} />
        </div>
      </section>

      <section className="legal-sec">
        <h2 className="t-title">문의하기</h2>
        <p>
          {contact === TBD
            ? '고객센터 연락처와 운영 시간은 정식 서비스 전에 안내합니다. 그전까지는 예약 상세의 안내 메일을 확인해주세요.'
            : `고객센터 ${contact}`}
        </p>
      </section>
    </div>
  )
}
