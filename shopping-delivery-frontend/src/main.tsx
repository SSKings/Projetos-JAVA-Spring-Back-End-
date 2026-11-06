// Observabilidade (ADR-022): precisa rodar antes de qualquer requisição, por isso fica no topo e o render
// espera. Desligada por padrão (VITE_OTEL_ENABLED); ligada, carrega o SDK OpenTelemetry num chunk separado.
import { resolveObservabilityConfig } from './observability/config'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

const otel = resolveObservabilityConfig(import.meta.env, window.location)
if (otel) {
  try {
    const { initObservability } = await import('./observability/instrumentation')
    initObservability(otel)
  } catch (err) {
    // telemetria nunca pode impedir a loja de abrir
    console.warn('Observabilidade desligada: falha ao iniciar o OpenTelemetry', err)
  }
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
