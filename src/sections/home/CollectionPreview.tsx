import SectionHeader from '@/components/SectionHeader'
import ProductCard from '@/components/ProductCard'
import { GRADES, INSPECTION_CHECKS } from '@/data/carriers'
import { useCatalog } from '@/lib/catalog'

export default function CollectionPreview() {
  const { list, error, retry } = useCatalog()

  return (
    <section className="sec" id="collection">
      <div className="wrap">
        <div className="grid col-head" data-reveal>
          <div className="l">
            <SectionHeader index="03" eyebrow="Collection" title={['세 가지 사이즈,', '하나의 검수 기준.']} />
          </div>
          <p className="r muted">
            모든 캐리어는 {INSPECTION_CHECKS.length}개 항목 검수를 마친 뒤에만 컬렉션에 올라옵니다. 가격은 2박 3일 기준입니다.
          </p>
        </div>

        {error ? (
          <p className="note" style={{ marginTop: 'var(--space-8)' }} role="alert">
            <span>{error}</span>
            <button className="link" onClick={retry}>
              다시 불러오기
            </button>
          </p>
        ) : (
          <div className="cards" data-reveal="stagger" aria-busy={!list}>
            {list?.map((c) => <ProductCard key={c.key} carrier={c} />)}
          </div>
        )}

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
