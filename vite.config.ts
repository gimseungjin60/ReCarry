import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // '/src' is project-root-relative — avoids needing @types/node for path.resolve
  resolve: { alias: { '@': '/src' } },
})
