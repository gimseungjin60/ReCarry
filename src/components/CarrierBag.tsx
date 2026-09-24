import { useId } from 'react'
import type { CSSProperties } from 'react'
import type { BagColor, Carrier } from '@/data/types'

/* prototype 의 bag() SVG 생성기를 컴포넌트로 옮긴 것.
   좌표·그라디언트·비율은 원본 그대로다. 형태를 새로 디자인하지 않는다.
   전역 UID 카운터는 useId() 로 대체했다. */

const PALETTE: Record<BagColor, { shell: string; light: string; dark: string }> = {
  graphite: { shell: '#3c3d3e', light: '#5d5f61', dark: '#222324' },
  chalk: { shell: '#e4dfd4', light: '#f4f1eb', dark: '#c3bcad' },
  clay: { shell: '#a5624a', light: '#bd7d64', dark: '#7c4834' },
}

/** 크롭 viewBox 를 계산할 때 쓰는 기준 좌표 */
export interface BagGeometry {
  x: number
  cx: number
  top: number
  bot: number
  gy: number
  H: number
}

export interface CarrierBagProps {
  color?: BagColor
  width?: number
  height?: number
  /** 손잡이를 올린 상태 / 내린 상태 */
  handle?: 'up' | 'down'
  handleExtend?: number
  /** RECARRY 태그를 매단 모습 */
  tag?: boolean
  tagColor?: string
  /** 회수 당시(수리 전) 모습 */
  worn?: boolean
  /** 문자열이면 그대로, 함수면 좌표를 받아 크롭 viewBox 를 만든다 */
  viewBox?: string | ((g: BagGeometry) => string)
  className?: string
  style?: CSSProperties
  label?: string
}

const W = 240

export default function CarrierBag({
  color = 'graphite',
  width = 160,
  height = 250,
  handle = 'up',
  handleExtend = 64,
  tag = false,
  tagColor = '#c4592b',
  worn = false,
  viewBox,
  className = 'bag',
  style,
  label = 'RECARRY 캐리어',
}: CarrierBagProps) {
  const uid = useId().replace(/:/g, '')
  const c = PALETTE[color]

  const bw = width
  const bh = height
  const x = (W - bw) / 2
  const cx = W / 2
  const top = 110
  const bot = top + bh
  const H = bot + 46
  const ext = handle === 'up' ? handleExtend : 0
  const gy = top - 16 - ext

  const geo: BagGeometry = { x, cx, top, bot, gy, H }
  const vb = typeof viewBox === 'function' ? viewBox(geo) : viewBox || `0 0 ${W} ${H}`

  const wheelX = [x + 8, x + bw - 38]
  const ribs = [0.22, 0.5, 0.78]

  const scratches =
    `M${x + 22} ${top + 64} l30 -9 ` +
    `M${x + bw - 54} ${top + 140} l22 12 ` +
    `M${x + 34} ${bot - 78} l34 5 ` +
    `M${x + bw * 0.55} ${top + 40} l-14 26`

  const tagPath =
    `M${cx + 28} ${top - 3} C ${cx + 46} ${top - 8}, ` +
    `${cx + 58} ${top + 12}, ${cx + 58} ${top + 30}`

  return (
    <svg
      className={className}
      style={style}
      viewBox={vb}
      xmlns="http://www.w3.org/2000/svg"
      role="img"
      aria-label={label}
      preserveAspectRatio={'xMidYMid ' + (viewBox ? 'slice' : 'meet')}
    >
      <defs>
        <linearGradient id={uid + 's'} x1="0" x2="1">
          <stop offset="0" stopColor={c.light} />
          <stop offset=".14" stopColor={c.shell} />
          <stop offset=".8" stopColor={c.shell} />
          <stop offset="1" stopColor={c.dark} />
        </linearGradient>
        <linearGradient id={uid + 'v'} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#fff" stopOpacity=".12" />
          <stop offset=".45" stopColor="#fff" stopOpacity="0" />
          <stop offset="1" stopColor="#000" stopOpacity=".12" />
        </linearGradient>
        <linearGradient id={uid + 'r'} x1="0" x2="1">
          <stop offset="0" stopColor={c.light} />
          <stop offset=".55" stopColor={c.shell} />
          <stop offset="1" stopColor={c.dark} />
        </linearGradient>
        <linearGradient id={uid + 'm'} x1="0" x2="1">
          <stop offset="0" stopColor="#8a8a88" />
          <stop offset=".5" stopColor="#dcdcd9" />
          <stop offset="1" stopColor="#737371" />
        </linearGradient>
        {worn && (
          <filter id={uid + 'f'}>
            <feColorMatrix type="saturate" values=".3" />
          </filter>
        )}
      </defs>

      {/* 바닥 그림자 */}
      <ellipse cx={cx} cy={bot + 32} rx={bw * 0.74} ry={9} fill="#000" opacity=".07" />
      <ellipse cx={cx} cy={bot + 31} rx={bw * 0.56} ry={4.5} fill="#000" opacity=".17" />

      <g filter={worn ? 'url(#' + uid + 'f)' : undefined}>
        {ext > 0 && (
          <>
            <rect x={cx - 27} y={gy + 12} width={5} height={top - gy - 12} fill={'url(#' + uid + 'm)'} />
            <rect x={cx + 22} y={gy + 12} width={5} height={top - gy - 12} fill={'url(#' + uid + 'm)'} />
          </>
        )}

        {/* 손잡이 그립 · 상단 레일 */}
        <rect x={cx - 36} y={gy} width={72} height={16} rx={8} fill="#1c1c1b" />
        <rect x={cx - 42} y={top - 5} width={84} height={9} rx={3} fill={c.dark} />

        {/* 바퀴. worn 일 때 오른쪽 바퀴 하나가 빠져 있다 — 버려지는 전형적인 이유 */}
        {wheelX.map((hx, i) => (
          <g key={i}>
            <rect x={hx} y={bot - 6} width={30} height={16} rx={4} fill="#1e1e1d" />
            {!(worn && i === 1) && (
              <>
                <circle cx={hx + 15} cy={bot + 17} r={11} fill="#1a1a19" />
                <circle cx={hx + 15} cy={bot + 17} r={4} fill="#4a4a48" />
              </>
            )}
          </g>
        ))}

        {/* 쉘 */}
        <rect x={x} y={top} width={bw} height={bh} rx={18} fill={'url(#' + uid + 's)'} />
        <rect x={x} y={top} width={bw} height={bh} rx={18} fill={'url(#' + uid + 'v)'} />
        <rect
          x={x + 5}
          y={top + 5}
          width={bw - 10}
          height={bh - 10}
          rx={14}
          fill="none"
          stroke={c.dark}
          strokeOpacity=".28"
        />

        {/* 세로 리브 3줄 */}
        {ribs.map((p, i) => {
          const rx = x + bw * p - 7
          return (
            <g key={i}>
              <rect x={rx} y={top + 22} width={14} height={bh - 44} rx={7} fill={'url(#' + uid + 'r)'} />
              <rect x={rx + 15} y={top + 26} width={1.2} height={bh - 52} fill={c.dark} opacity=".45" />
            </g>
          )
        })}

        {/* 사용 흔적: 변색, 얼룩, 스크래치, 떨어지다 만 스티커 */}
        {worn && (
          <>
            <rect x={x} y={top} width={bw} height={bh} rx={18} fill="#8d877b" opacity=".28" />
            <ellipse cx={x + bw * 0.3} cy={bot - 40} rx={34} ry={18} fill="#6f685d" opacity=".35" />
            <ellipse cx={x + bw * 0.75} cy={top + 70} rx={22} ry={30} fill="#6f685d" opacity=".25" />
            <path d={scratches} stroke="#fff" strokeOpacity=".55" strokeWidth={1.3} />
            <g transform={'rotate(-6 ' + (x + bw * 0.36) + ' ' + (top + bh * 0.5) + ')'}>
              <rect x={x + bw * 0.2} y={top + bh * 0.46} width={46} height={26} fill="#ece6d8" opacity=".85" />
              <rect x={x + bw * 0.2 + 6} y={top + bh * 0.46 + 7} width={30} height={3} fill="#8d877b" />
              <rect x={x + bw * 0.2 + 6} y={top + bh * 0.46 + 14} width={20} height={3} fill="#8d877b" />
            </g>
          </>
        )}
      </g>

      {/* RECARRY 태그 */}
      {tag && (
        <>
          <path d={tagPath} stroke="#1c1c1b" strokeWidth={1.4} fill="none" />
          <g transform={'translate(' + (cx + 43) + ' ' + (top + 28) + ') rotate(9)'}>
            <rect width={30} height={50} rx={3} fill={tagColor} />
            <circle cx={15} cy={8} r={2.8} fill="#1c1c1b" opacity=".55" />
            <rect x={6} y={18} width={18} height={2} fill="#fff" opacity=".9" />
            <rect x={6} y={24} width={12} height={2} fill="#fff" opacity=".65" />
            <rect x={6} y={36} width={2} height={8} fill="#fff" />
            <rect x={10} y={36} width={1} height={8} fill="#fff" />
            <rect x={13} y={36} width={3} height={8} fill="#fff" />
            <rect x={18} y={36} width={1} height={8} fill="#fff" />
            <rect x={21} y={36} width={2} height={8} fill="#fff" />
          </g>
        </>
      )}
    </svg>
  )
}

/** 캐리어 데이터로 채운 CarrierBag. prototype 의 pbag() 대응. */
export function ProductBag({ carrier, ...rest }: { carrier: Carrier } & CarrierBagProps) {
  return (
    <CarrierBag
      color={carrier.color}
      width={carrier.bagWidth}
      height={carrier.bagHeight}
      label={carrier.name}
      {...rest}
    />
  )
}

/** 사진이 준비되면 사진을, 아니면 일러스트를 보여준다. prototype 의 media() 대응.
    실사진으로 교체해도 레이아웃은 그대로다. */
export function CarrierMedia({ carrier, ...rest }: { carrier: Carrier } & CarrierBagProps) {
  if (carrier.photo) {
    return (
      <img
        className="ph"
        src={carrier.photo}
        alt={carrier.name}
        style={{ objectPosition: carrier.photoPos }}
        decoding="async"
      />
    )
  }
  return <ProductBag carrier={carrier} {...rest} />
}
