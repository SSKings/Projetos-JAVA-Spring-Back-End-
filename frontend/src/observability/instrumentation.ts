import { resourceFromAttributes } from '@opentelemetry/resources';
import { ATTR_SERVICE_NAME } from '@opentelemetry/semantic-conventions';
import { BatchSpanProcessor, WebTracerProvider, type SpanExporter } from '@opentelemetry/sdk-trace-web';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { registerInstrumentations } from '@opentelemetry/instrumentation';
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch';
import { XMLHttpRequestInstrumentation } from '@opentelemetry/instrumentation-xml-http-request';
import type { ObservabilityConfig } from './config';

/**
 * Liga o tracing do navegador (seção 3.2 da especificação, ajustada para o SDK 2.x — ADR-022):
 * - resourceFromAttributes + spanProcessors no construtor (o SDK 2.x removeu `new Resource` e addSpanProcessor);
 * - BatchSpanProcessor em vez de SimpleSpanProcessor (um envio HTTP por span travaria a página);
 * - XMLHttpRequestInstrumentation além da Fetch: o axios usa XHR, então só a Fetch não veria as chamadas à API.
 *
 * Carregado só quando ligado (import dinâmico em main.tsx): com a instrumentação desligada nada disso entra
 * no bundle principal.
 */
export interface Observability {
  provider: WebTracerProvider;
  /** Desfaz o patch de XHR/fetch e envia os spans pendentes. */
  shutdown: () => Promise<void>;
}

export function initObservability(
  config: ObservabilityConfig,
  exporter: SpanExporter = new OTLPTraceExporter({ url: config.exporterUrl }),
): Observability {
  const provider = new WebTracerProvider({
    resource: resourceFromAttributes({ [ATTR_SERVICE_NAME]: config.serviceName }),
    spanProcessors: [new BatchSpanProcessor(exporter)],
  });
  provider.register();

  const common = {
    propagateTraceHeaderCorsUrls: config.propagateTraceHeaderCorsUrls,
    ignoreUrls: config.ignoreUrls,
  };
  const unregister = registerInstrumentations({
    tracerProvider: provider,
    instrumentations: [new XMLHttpRequestInstrumentation(common), new FetchInstrumentation(common)],
  });

  return {
    provider,
    shutdown: async () => {
      unregister();
      await provider.shutdown();
    },
  };
}
