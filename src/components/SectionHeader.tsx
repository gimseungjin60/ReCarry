import type { ReactNode } from 'react'

/* prototype 의 반복 패턴:
     <p class="label idx">01 — Why RECARRY</p>
     <h2 class="d-l" style="margin-top:32px">…</h2>
   Home 7개 섹션과 Product 하위 섹션에서 동일하게 쓰인다. */

interface Props {
  /** '01' 같은 섹션 번호. 없으면 라벨만 표시한다. */
  index?: string
  eyebrow: string
  /** 줄바꿈은 배열로 넘긴다 */
  title?: string[]
  as?: 'h1' | 'h2'
  size?: 'd-l' | 'd-m'
  children?: ReactNode
}

export default function SectionHeader({
  index,
  eyebrow,
  title,
  as: Heading = 'h2',
  size = 'd-l',
  children,
}: Props) {
  return (
    <>
      <p className="label idx">{index ? `${index} — ${eyebrow}` : eyebrow}</p>
      {title && (
        <Heading className={size} style={{ marginTop: size === 'd-m' ? 24 : 32 }}>
          {title.map((line, i) => (
            <span key={i}>
              {i > 0 && <br />}
              {line}
            </span>
          ))}
        </Heading>
      )}
      {children}
    </>
  )
}
