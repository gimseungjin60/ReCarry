import { useState } from 'react'
import SectionHeader from '@/components/SectionHeader'
import { PROCESS_STEPS } from '@/data/process'

/* prototype ringSVG() 좌표 그대로: 중심 280, 반지름 200, 노드는 -90°부터 60° 간격.
   자동 회전은 아직 없다 — 목록을 누르면 해당 단계로 이동한다. */
const C = 280
const R = 200
const CIRC = 2 * Math.PI * R
const N = PROCESS_STEPS.length

const POINTS = PROCESS_STEPS.map((_, i) => {
  const a = ((-90 + i * 60) * Math.PI) / 180
  const cos = Math.cos(a)
  const sin = Math.sin(a)
  return {
    nx: (C + R * cos).toFixed(1),
    ny: (C + R * sin).toFixed(1),
    lx: (C + (R + 40) * cos).toFixed(1),
    ly: (C + (R + 40) * sin + 4).toFixed(1),
    anchor: (Math.abs(cos) < 0.2 ? 'middle' : cos > 0 ? 'start' : 'end') as 'middle' | 'start' | 'end',
  }
})

export default function CircularProcess() {
  const [active, setActive] = useState(0)
  // traveler 점은 항상 시계방향으로만 돈다
  const [rot, setRot] = useState(0)
  const step = PROCESS_STEPS[active]

  const go = (i: number) => {
    setRot((r) => r + ((i - active + N) % N) * (360 / N))
    setActive(i)
  }

  return (
    <section className="sec blk-forest" id="how">
      <div className="wrap">
        <div className="grid">
          <div className="proc-head" data-reveal>
            <SectionHeader
              index="02"
              eyebrow="How it works"
              title={['하나의 여행이 끝나면,', '다음 여행이 시작됩니다.']}
            />
            <p className="muted">모든 RECARRY 캐리어는 같은 순환을 거칩니다. 반납된 캐리어는 다시 처음으로 돌아갑니다.</p>
          </div>
        </div>

        <div className="grid proc-body" data-reveal>
          <div className="ring-wrap">
            <svg viewBox="-80 0 720 560" aria-hidden="true">
              <circle className="ring-track" cx={C} cy={C} r={R} />
              <circle
                className="ring-prog"
                cx={C}
                cy={C}
                r={R}
                strokeDasharray={CIRC.toFixed(1)}
                style={{ strokeDashoffset: (CIRC * (1 - active / N)).toFixed(1) }}
                transform={`rotate(-90 ${C} ${C})`}
              />
              {POINTS.map((p, i) => (
                <circle key={i} className={i <= active ? 'ring-node done' : 'ring-node'} cx={p.nx} cy={p.ny} r="6" />
              ))}
              {POINTS.map((p, i) => (
                <text
                  key={i}
                  className={i === active ? 'ring-label on' : 'ring-label'}
                  x={p.lx}
                  y={p.ly}
                  textAnchor={p.anchor}
                >
                  {PROCESS_STEPS[i].en}
                </text>
              ))}
              <g className="ring-traveler" style={{ transformOrigin: `${C}px ${C}px`, transform: `rotate(${rot}deg)` }}>
                <circle cx={C} cy={C - R} r="11" fill="var(--signal)" />
              </g>
            </svg>
            <div className="ring-center" aria-live="polite">
              <span className="n">
                0{active + 1} / 0{N}
              </span>
              <span className="w">{step.ko}</span>
              <span className="e">{step.en}</span>
            </div>
          </div>

          <ul className="proc-list">
            {PROCESS_STEPS.map((s, i) => (
              <li key={s.ko} className={i === active ? 'on' : undefined}>
                <button onClick={() => go(i)} aria-pressed={i === active}>
                  <span className="mono">0{i + 1}</span>
                  <span className="nm">{s.ko}</span>
                  <span className="label">{s.en}</span>
                  <span className="desc">{s.desc}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  )
}
