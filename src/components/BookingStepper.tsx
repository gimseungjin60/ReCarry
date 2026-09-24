import { BOOKING_STEPS } from '@/data/process'
import type { BookingStep } from '@/data/process'
import './BookingStepper.css'

/* 데스크톱은 4분할 탭, 모바일은 진행률 바. prototype 과 동일한 전환. */

interface Props {
  step: BookingStep
  /** 지금까지 도달한 최대 단계 — 그 뒤로는 건너뛸 수 없다 */
  maxStep: BookingStep
  onGoto: (step: BookingStep) => void
}

export default function BookingStepper({ step, maxStep, onGoto }: Props) {
  return (
    <>
      <div className="stepper">
        {BOOKING_STEPS.map((label, i) => {
          const n = (i + 1) as BookingStep
          const state = n === step ? 'on' : n < step ? 'done' : ''
          return (
            <button
              key={label}
              className={state}
              disabled={n > maxStep}
              aria-current={n === step ? 'step' : undefined}
              onClick={() => onGoto(n)}
            >
              <span className="label s">Step 0{n}</span>
              <span className="t">{label}</span>
            </button>
          )
        })}
      </div>

      <div className="stepper-m">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
          <span className="label">Step 0{step} / 04</span>
          <span style={{ fontSize: 15, fontWeight: 500 }}>{BOOKING_STEPS[step - 1]}</span>
        </div>
        <div className="bar">
          <i style={{ width: `${step * 25}%` }} />
        </div>
      </div>
    </>
  )
}
