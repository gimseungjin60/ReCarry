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
            <SectionHeader index="01" eyebrow="Sizes" title={['여행에 맞는', '사이즈를 고르세요.']} />
          </div>
          <p className="r muted">
            가격은 2박 3일 기준이며 3박째부터 1박 추가 요금이 붙습니다. 보증금은 대여 요금의 30%로 반납 검수 후 돌려드립니다. 모든
            캐리어는 {INSPECTION_CHECKS.length}개 항목을 검수합니다.
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
