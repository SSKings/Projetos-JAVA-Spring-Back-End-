import { resolveApiBaseUrl } from '../api/baseUrl';

/** Nome do serviço nos traces (o back usa backend-spring; ver scripts/run-backend.sh). */
export const DEFAULT_SERVICE_NAME = 'frontend-react';

/**
 * Caminho do proxy do Vite para o OpenTelemetry Collector (vite.config.ts → /otlp → localhost:4318).
 * Mesma origem: não precisa de CORS no collector nem liberar outra origem no connect-src da CSP.
 */
export const DEFAULT_EXPORTER_PATH = '/otlp/v1/traces';

export interface ObservabilityEnv {
  VITE_OTEL_ENABLED?: string;
  VITE_OTEL_SERVICE_NAME?: string;
  VITE_OTEL_EXPORTER_URL?: string;
  VITE_API_BASE_URL?: string;
  /** aceita o import.meta.env inteiro (que tem outras chaves) */
  [key: string]: unknown;
}

export interface ObservabilityConfig {
  serviceName: string;
  /** URL absoluta para onde os spans são enviados (OTLP/HTTP). */
  exporterUrl: string;
  /** Requisições para estas URLs recebem o cabeçalho traceparent (correlação front → back). */
  propagateTraceHeaderCorsUrls: RegExp[];
  /** Requisições que não viram span: o próprio envio de spans (senão cada envio geraria outro span). */
  ignoreUrls: RegExp[];
}

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Decide se a instrumentação liga e com que parâmetros. Desligada por padrão (opt-in com
 * VITE_OTEL_ENABLED=true): sem collector rodando, o navegador ficaria tentando enviar spans à toa.
 *
 * Diferenças em relação à especificação (docs/instrunctions-cloud-impl-observability.md, ADR-022):
 * - o traceparent só vai para a API desta aplicação (mesma regra de resolveApiBaseUrl), não para qualquer
 *   URL com "localhost:8080" — funciona também ao abrir o front pelo IP da rede;
 * - o exporter usa o proxy /otlp (mesma origem) em vez de http://localhost:4318 fixo.
 */
export function resolveObservabilityConfig(
  env: ObservabilityEnv,
  location: Pick<Location, 'protocol' | 'hostname' | 'origin'>,
): ObservabilityConfig | null {
  if (env.VITE_OTEL_ENABLED?.trim().toLowerCase() !== 'true') return null;

  const exporterUrl = new URL(env.VITE_OTEL_EXPORTER_URL?.trim() || DEFAULT_EXPORTER_PATH, location.origin).href;
  // '' = API na mesma origem (atrás do nginx): a instrumentação já propaga para a própria origem
  const apiBase = resolveApiBaseUrl(env.VITE_API_BASE_URL, location) || location.origin;

  return {
    serviceName: env.VITE_OTEL_SERVICE_NAME?.trim() || DEFAULT_SERVICE_NAME,
    exporterUrl,
    propagateTraceHeaderCorsUrls: [new RegExp(`^${escapeRegExp(apiBase)}(/|$)`)],
    ignoreUrls: [new RegExp(`^${escapeRegExp(exporterUrl)}`)],
  };
}
