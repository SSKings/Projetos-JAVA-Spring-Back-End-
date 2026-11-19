import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// Observabilidade (ADR-022): o navegador envia spans para /otlp (mesma origem) e o Vite repassa ao collector
// da observability-stack. Só existe no servidor de desenvolvimento.
const otlpProxy = {
  '/otlp': {
    target: process.env.OTEL_COLLECTOR_URL ?? 'http://localhost:4318',
    changeOrigin: true,
    rewrite: (path: string) => path.replace(/^\/otlp/, ''),
  },
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // host: true → escuta em todas as interfaces (0.0.0.0 / ::), não só em localhost,
  // para que outros computadores da rede acessem http://<ip-desta-máquina>:5173
  server: { host: true, port: 5173, strictPort: true, proxy: otlpProxy },
  preview: { host: true, port: 4173, strictPort: true },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json-summary'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/main.tsx', 'src/types/**', 'src/test/**', 'src/**/*.test.{ts,tsx}'],
      // npm run coverage falha se a cobertura cair abaixo destes valores
      thresholds: { lines: 97, branches: 94, functions: 95, statements: 97 },   // perto do atual (99,1 / 95,9 / 97,0 / 98,4)
    },
  },
})
