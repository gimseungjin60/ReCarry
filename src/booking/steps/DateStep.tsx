import Calendar from '@/components/Calendar'
import type { DateRange } from '@/components/Calendar'

/* Step 01 여행 날짜 — 데스크톱은 두 달, 모바일은 CSS 가 한 달로 줄인다 */

export default function DateStep({ range, onChange }: { range: DateRange; onChange: (r: DateRange) => void }) {
  return (
    <>
      <h2 className="t-title">언제 여행을 떠나시나요?</h2>
      <p className="small" style={{ margin: '8px 0 32px' }}>
        출발일과 도착일을 선택하세요. 기본 요금은 2박 3일 기준이며, 캐리어는 출발 전날 도착합니다.
      </p>
      <Calendar value={range} onChange={onChange} months={2} />
    </>
  )
}
