import { Fragment } from 'react'
import Button from '@/components/Button'
import { CARRIERS, DEFAULT_SIZE } from '@/data/carriers'
import { PROCESS_STEPS } from '@/data/process'
import { HOME_IMAGES } from '@/data/home'
import { useAnchorNav } from '@/lib/useAnchorNav'

/* V2 정적 hero. 스크롤 확장 애니메이션은 이후 단계에서 이 자리에 붙인다. */

export default function Hero() {
  const goAnchor = useAnchorNav()
  const c = CARRIERS[DEFAULT_SIZE]

  return (
    <section className="hero2" aria-label="RECARRY 소개">
      <img className="ph" src={HOME_IMAGES.hero.src} alt={HOME_IMAGES.hero.alt} decoding="async" />
      <div className="wrap hero2-top">
        <p className="label">Circular carrier rental — Seoul</p>
        <p className="mono">
          {c.id} · {c.grade} GRADE · 2ND JOURNEY
        </p>
      </div>
      <div className="wrap grid hero2-in">
        <div className="hero2-copy">
          <h1 className="d-xl">
            버려지는 캐리어를
            <br />
            다시 여행으로.
          </h1>
          <p className="lead">
            사용되지 않고 버려질 캐리어를
            <br />
            새로운 여행의 시작으로 바꿉니다.
          </p>
          <div className="hero-actions">
            <Button variant="light" arrow onClick={() => goAnchor('collection')}>
              캐리어 둘러보기
            </Button>
            <button className="link-arrow" onClick={() => goAnchor('how')}>
              순환 과정 보기
            </button>
          </div>
        </div>
      </div>
      <p className="wrap mono hero2-loop">
        {PROCESS_STEPS.map((s, i) => (
          <Fragment key={s.ko}>
            {i > 0 && <span aria-hidden="true">→</span>}
            <b>{s.ko}</b>
          </Fragment>
        ))}
      </p>
    </section>
  )
}
