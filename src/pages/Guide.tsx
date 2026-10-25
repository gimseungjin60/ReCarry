import { Link } from 'react-router-dom'
import Button from '@/components/Button'
import SectionHeader from '@/components/SectionHeader'
import { useCatalog } from '@/lib/catalog'
import { won } from '@/lib/format'
import { useTitle } from '@/lib/useTitle'

/* 이용 안내 — 예약부터 보증금 반환까지 한 페이지에. 정확한 기준은 대여 정책(/policy)이 우선한다. */

const FLOW = [
  { t: '사이즈 · 날짜 선택', s: '20 · 24 · 28인치 중 여행에 맞는 크기와 출발일 · 도착일을 고릅니다. 로그인 없이도 금액까지 확인할 수 있습니다.' },
  { t: '예약 요청', s: '배송 정보를 적고 마지막 단계에서 로그인(카카오 또는 이메일)하면 예약 요청이 접수됩니다.' },
  { t: '입금', s: '안내받은 계좌로 대여 요금과 보증금을 3일 안에 입금합니다. 입금이 확인되면 확정 메일을 보내드립니다.' },
  { t: '출발 전날 도착', s: '세척 · 수리 · 검수를 마친 캐리어가 출발 전날까지 문 앞으로 갑니다.' },
  { t: '여행', s: '평소처럼 쓰세요. 바퀴 자국이나 미세한 긁힘 같은 사용 흔적에는 비용을 받지 않습니다.' },
  { t: '반납 · 보증금 반환', s: '돌아오는 날 같은 주소에서 회수하고, 반납 검수 후 영업일 3일 안에 보증금을 돌려드립니다.' },
]

export default function Guide() {
  useTitle('이용 안내')
  const { list } = useCatalog()

  return (
    <div className="wrap legal-pg guide-pg">
      <p className="label muted">How it works</p>
      <h1 className="d-m" style={{ marginTop: 12 }}>
        이용 안내
      </h1>
      <p className="muted" style={{ marginTop: 16 }}>
        고르고, 받고, 여행하고, 돌려주세요. 예약부터 보증금 반환까지의 과정입니다.
      </p>

      <section className="legal-sec">
        <SectionHeader eyebrow="Flow" title={['예약부터 반납까지']} size="d-m" />
        <ol className="flow">
          {FLOW.map((f, i) => (
            <li key={f.t}>
              <span className="mono muted">0{i + 1}</span>
              <div>
                <p className="t-title">{f.t}</p>
                <p className="small">{f.s}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      <section className="legal-sec" id="price">
        <SectionHeader eyebrow="Price" title={['사이즈와 요금']} size="d-m" />
        <div className="tbl-wrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>사이즈</th>
                <th>추천</th>
                <th>용량 · 무게</th>
                <th>2박 3일</th>
                <th>1박 추가</th>
              </tr>
            </thead>
            <tbody>
              {(list ?? []).map((c) => (
                <tr key={c.key}>
                  <td>
                    <Link className="link" to={`/product/${c.key}`}>
                      {c.key}"
                    </Link>
                  </td>
                  <td>{c.usage}</td>
                  <td>
                    {c.capacity} · {c.weight}
                  </td>
                  <td>{won(c.price)}</td>
                  <td>{won(c.extra)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <ul>
          <li>보증금은 대여 요금의 30%(100원 미만 버림)이고, 대여 요금과 함께 입금합니다.</li>
          <li>현재 가격은 sample 값입니다. 최종 금액은 예약 확인 단계에서 서버가 계산한 금액입니다.</li>
        </ul>
      </section>

      <section className="legal-sec" id="pay">
        <SectionHeader eyebrow="Payment" title={['결제 · 취소']} size="d-m" />
        <ul>
          <li>결제는 무통장 입금입니다. 예약 요청 후 화면과 메일로 계좌 · 금액 · 입금자명을 안내합니다.</li>
          <li>입금 전 예약 요청은 언제든 무료로 취소할 수 있습니다.</li>
          <li>입금 후 24시간 이내, 또는 배송 예정일 3일 전까지 취소하면 전액 환불됩니다.</li>
          <li>배송 전까지는 My Page 에서 직접 취소할 수 있고, 돌려받는 금액은 취소 전에 보여드립니다.</li>
        </ul>
        <p>
          단계별 기준은{' '}
          <Link className="link" to="/policy">
            대여 정책
          </Link>
          을 확인해주세요.
        </p>
      </section>

      <div style={{ marginTop: 48, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <Button to="/booking" arrow>
          예약하기
        </Button>
        <Button to="/help" variant="secondary">
          고객센터
        </Button>
      </div>
    </div>
  )
}
