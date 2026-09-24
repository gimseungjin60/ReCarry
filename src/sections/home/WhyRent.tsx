import SectionHeader from '@/components/SectionHeader'
import { HOME_IMAGES, RENT_REASONS } from '@/data/home'

export default function WhyRent() {
  return (
    <section className="sec" id="rent">
      <div className="wrap grid rent2">
        <figure className="rent-fig">
          <img className="ph" src={HOME_IMAGES.rent.src} alt={HOME_IMAGES.rent.alt} decoding="async" />
          <figcaption className="mono muted">Travel light — 필요한 기간만, 가볍게.</figcaption>
        </figure>
        <div className="rent-txt">
          <div className="rent-l">
            <SectionHeader index="05" eyebrow="Why rent" title={['굳이 새로', '구매하지 않아도', '됩니다.']} />
            <p className="lead">
              필요한 기간만 사용하고
              <br />
              다시 돌려보내세요.
            </p>
          </div>
          <ul className="rent-r">
            {RENT_REASONS.map((r, i) => (
              <li key={r.title}>
                <span className="mono n">0{i + 1}</span>
                <p className="t">{r.title}</p>
                <p className="s">{r.desc}</p>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  )
}
