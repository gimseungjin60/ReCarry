import { Link } from 'react-router-dom'
import type { ButtonHTMLAttributes, ReactNode } from 'react'

/* prototype 의 .btn 클래스 조합을 한 곳에 모은다.
   스타일은 styles/base.css 의 .btn / .btn-primary / .btn-secondary / .btn-light 그대로. */

type Variant = 'primary' | 'secondary' | 'light'

interface CommonProps {
  variant?: Variant
  /** 작은 버튼 (.btn-sm) */
  small?: boolean
  /** 가로 꽉 채움 (.btn-block) */
  block?: boolean
  /** 뒤에 붙는 → 화살표. hover 시 움직인다. */
  arrow?: boolean
  className?: string
  children: ReactNode
}

type ButtonProps = CommonProps &
  Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className' | 'children'> & { to?: undefined }

type LinkProps = CommonProps & { to: string }

function classes({ variant = 'primary', small, block, className }: CommonProps) {
  return ['btn', `btn-${variant}`, small && 'btn-sm', block && 'btn-block', className]
    .filter(Boolean)
    .join(' ')
}

export const Arrow = () => (
  <span className="arr" aria-hidden="true">
    →
  </span>
)

export default function Button(props: ButtonProps | LinkProps) {
  const { children, arrow, ...rest } = props
  const body = (
    <>
      {children}
      {arrow && <Arrow />}
    </>
  )

  if ('to' in rest && rest.to) {
    const { to, variant, small, block, className } = rest as LinkProps
    return (
      <Link to={to} className={classes({ variant, small, block, className, children })}>
        {body}
      </Link>
    )
  }

  const { variant, small, block, className, ...attrs } = rest as ButtonProps
  return (
    <button {...attrs} className={classes({ variant, small, block, className, children })}>
      {body}
    </button>
  )
}
