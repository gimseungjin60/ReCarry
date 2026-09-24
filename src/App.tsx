/* Step 0: 토큰·타이포·레이아웃 검증용 셸.
   라우팅과 실제 섹션은 Step 1에서 들어온다. */
export default function App() {
  return (
    <div id="app">
      <section className="sec">
        <div className="wrap">
          <p className="label idx">Step 00 — Foundation</p>
          <h1 className="d-xl" style={{ marginTop: 32 }}>
            버려지는 캐리어를<br />다시 여행으로.
          </h1>
          <p className="lead" style={{ marginTop: 32, maxWidth: 400 }}>
            디자인 토큰과 타이포그래피 스케일 검증용 화면입니다.
          </p>
          <div style={{ display: 'flex', gap: 16, marginTop: 48, flexWrap: 'wrap' }}>
            <button className="btn btn-primary">Primary <span className="arr">→</span></button>
            <button className="btn btn-secondary">Secondary</button>
            <span className="tag">A Grade</span>
          </div>
          <p className="mono muted" style={{ marginTop: 48 }}>RC-24-0187 · TOKENS OK</p>
        </div>
      </section>
    </div>
  )
}
