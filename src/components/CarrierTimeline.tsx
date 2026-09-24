import type { StoryEntry } from '@/data/types'
import './CarrierTimeline.css'

/* 캐리어 한 대의 회수 → 수리 → 세척 → 검수 → 새로운 여행 기록.
   기본은 night 블록용, light 는 흰 배경(Product)용. */

export default function CarrierTimeline({ story, light }: { story: StoryEntry[]; light?: boolean }) {
  return (
    <ol className={light ? 'tl light' : 'tl'}>
      {story.map((s, i) => (
        <li key={s.date} className={i === story.length - 1 ? 'last' : undefined}>
          <span className="d">{s.date}</span>
          <div>
            <p className="h">{s.title}</p>
            <p className="s">{s.detail}</p>
          </div>
        </li>
      ))}
    </ol>
  )
}
