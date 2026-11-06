import { afterEach, describe, expect, it, vi } from 'vitest';
import { trace } from '@opentelemetry/api';
import { InMemorySpanExporter } from '@opentelemetry/sdk-trace-web';
import { resolveObservabilityConfig } from './config';
import { initObservability, type Observability } from './instrumentation';

const location = { protocol: 'http:', hostname: 'localhost', origin: 'http://localhost:5173' };
const config = resolveObservabilityConfig({ VITE_OTEL_ENABLED: 'true' }, location)!;

/** Abre e envia um XHR e devolve os cabeçalhos que a aplicação (ou a instrumentação) colocou nele. */
function sentHeaders(url: string): Record<string, string> {
  const headers: Record<string, string> = {};
  const spy = vi.spyOn(XMLHttpRequest.prototype, 'setRequestHeader').mockImplementation(function (name, value) {
    headers[name.toLowerCase()] = value;
  });
  const xhr = new XMLHttpRequest();
  xhr.open('GET', url);
  // a instrumentação adiciona os cabeçalhos dentro do send, antes de chamar o send do jsdom
  xhr.send();
  xhr.abort();
  spy.mockRestore();
  return headers;
}

describe('initObservability', () => {
  let obs: Observability | undefined;

  afterEach(async () => {
    await obs?.shutdown();
    obs = undefined;
    trace.disable();
    vi.restoreAllMocks();
  });

  it('registra o provider global com o nome do serviço e exporta os spans', async () => {
    const exporter = new InMemorySpanExporter();
    obs = initObservability(config, exporter);

    trace.getTracer('teste').startSpan('carregar-produtos').end();
    await obs.provider.forceFlush();

    const [span] = exporter.getFinishedSpans();
    expect(span.name).toBe('carregar-produtos');
    expect(span.resource.attributes['service.name']).toBe('frontend-react');
  });

  it('XHR para a API (axios) leva o traceparent W3C', () => {
    obs = initObservability(config, new InMemorySpanExporter());
    const headers = sentHeaders('http://localhost:8080/api/products');
    expect(headers.traceparent).toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]$/);
  });

  it('XHR para outro host não recebe o traceparent', () => {
    obs = initObservability(config, new InMemorySpanExporter());
    expect(sentHeaders('https://terceiro.example/pixel').traceparent).toBeUndefined();
  });

  it('sem a instrumentação nenhum traceparent é adicionado', () => {
    expect(sentHeaders('http://localhost:8080/api/products').traceparent).toBeUndefined();
  });
});
