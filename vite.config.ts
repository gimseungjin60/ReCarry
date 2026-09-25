import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // '/src' is project-root-relative — avoids needing @types/node for path.resolve
  resolve: { alias: { '@': '/src' } },
  // 개발 중에는 /api 를 로컬 Spring Boot 로 넘긴다 (운영은 VITE_API_BASE_URL / Vercel rewrite)
  // changeOrigin: false — Host 를 그대로 둬서 backend 가 같은 origin 요청으로 본다 (CORS 목록에 localhost 불필요)
  server: { proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: false } } },
})
