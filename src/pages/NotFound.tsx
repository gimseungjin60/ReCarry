import Button from '@/components/Button'
import { useTitle } from '@/lib/useTitle'

/* 없는 주소 — 조용히 홈으로 보내지 않고 알려준다 */

export default function NotFound() {
  useTitle('페이지를 찾을 수 없습니다')
  return (
    <div className="wrap pd-loading">
      <p className="label muted">404</p>
      <h1 className="d-m" style={{ marginTop: 16 }}>
        찾으시는 페이지가 없습니다.
      </h1>
      <p className="small" style={{ marginTop: 12 }}>
        주소가 바뀌었거나 잘못 입력되었을 수 있습니다.
      </p>
      <div style={{ marginTop: 32, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <Button to="/" arrow>
          홈으로
        </Button>
        <Button to="/policy" variant="secondary">
          대여 정책 보기
        </Button>
      </div>
    </div>
  )
}
