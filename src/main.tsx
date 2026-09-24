import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// base CSS 가 먼저 로드되어야 컴포넌트·섹션 CSS 가 prototype 과 같은 순서로 덮어쓴다
import './index.css'
import App from './App'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
