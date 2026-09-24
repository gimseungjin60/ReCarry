import SectionHeader from '@/components/SectionHeader'
import ProductCard from '@/components/ProductCard'
import { CARRIER_LIST, GRADES } from '@/data/carriers'

export default function CollectionPreview() {
  return (
    <section className="sec" id="collection">
      <div className="wrap">
        <div className="grid col-head">
          <div className="l">
            <SectionHeader index="03" eyebrow="Collection" title={['세 가지 사이즈,', '하나의 검수 기준.']} />
          </div>
          <p className="r muted">
            모든 캐리어는 세척과 12개 항목 검수를 마친 뒤에만 컬렉션에 올라옵니다. 가격은 2박 3일 기준입니다.
          </p>
        </div>

        <div className="cards">
          {CARRIER_LIST.map((c) => (
            <ProductCard key={c.key} carrier={c} />
          ))}
        </div>

        <div className="grades">
          {GRADES.map((g) => (
            <span key={g.grade}>
              <b>{g.grade}</b>
              {g.desc}
            </span>
          ))}
        </div>
      </div>
    </section>
  )
}
