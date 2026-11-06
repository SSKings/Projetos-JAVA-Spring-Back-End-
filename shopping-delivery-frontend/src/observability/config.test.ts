import { describe, expect, it } from 'vitest';
import { DEFAULT_SERVICE_NAME, resolveObservabilityConfig } from './config';

const local = { protocol: 'http:', hostname: 'localhost', origin: 'http://localhost:5173' };
const lan = { protocol: 'http:', hostname: '192.168.1.180', origin: 'http://192.168.1.180:5173' };

describe('resolveObservabilityConfig', () => {
  it.each([undefined, '', 'false', '1', 'yes'])('fica desligada quando VITE_OTEL_ENABLED=%s', value => {
    expect(resolveObservabilityConfig({ VITE_OTEL_ENABLED: value }, local)).toBeNull();
  });

  it('liga com VITE_OTEL_ENABLED=true (ignora maiúsculas e espaços)', () => {
    expect(resolveObservabilityConfig({ VITE_OTEL_ENABLED: ' TRUE ' }, local)).not.toBeNull();
  });

  it('usa o proxy /otlp da mesma origem e o nome de serviço padrão', () => {
    const cfg = resolveObservabilityConfig({ VITE_OTEL_ENABLED: 'true' }, local)!;
    expect(cfg.serviceName).toBe(DEFAULT_SERVICE_NAME);
    expect(cfg.exporterUrl).toBe('http://localhost:5173/otlp/v1/traces');
  });

  it('aceita nome de serviço e exporter configurados', () => {
    const cfg = resolveObservabilityConfig(
      { VITE_OTEL_ENABLED: 'true', VITE_OTEL_SERVICE_NAME: 'loja-web', VITE_OTEL_EXPORTER_URL: 'http://localhost:4318/v1/traces' },
      local,
    )!;
    expect(cfg.serviceName).toBe('loja-web');
    expect(cfg.exporterUrl).toBe('http://localhost:4318/v1/traces');
  });

  it('propaga traceparent só para a API desta aplicação (mesmo host da página, porta 8080)', () => {
    const [api] = resolveObservabilityConfig({ VITE_OTEL_ENABLED: 'true' }, lan)!.propagateTraceHeaderCorsUrls;
    expect(api.test('http://192.168.1.180:8080/api/cart')).toBe(true);
    expect(api.test('http://192.168.1.180:8080')).toBe(true);
    // não vaza o trace para terceiros nem para hosts parecidos
    expect(api.test('http://192.168.1.180:80800/api')).toBe(false);
    expect(api.test('http://192.168.1.18:8080/api')).toBe(false);
    expect(api.test('http://192x168x1x180:8080/api')).toBe(false);
    expect(api.test('https://evil.example/?u=http://192.168.1.180:8080/')).toBe(false);
  });

  it('com a API na mesma origem (VITE_API_BASE_URL=/), propaga para a própria origem', () => {
    const [api] = resolveObservabilityConfig({ VITE_OTEL_ENABLED: 'true', VITE_API_BASE_URL: '/' }, lan)!
      .propagateTraceHeaderCorsUrls;
    expect(api.test('http://192.168.1.180:5173/api/products')).toBe(true);
  });

  it('com API em outro domínio, propaga para ele', () => {
    const [api] = resolveObservabilityConfig(
      { VITE_OTEL_ENABLED: 'true', VITE_API_BASE_URL: 'https://api.loja.com.br/' },
      local,
    )!.propagateTraceHeaderCorsUrls;
    expect(api.test('https://api.loja.com.br/api/orders')).toBe(true);
    expect(api.test('https://api.loja.com.br.evil.example/api')).toBe(false);
  });

  it('não instrumenta o próprio envio de spans', () => {
    const cfg = resolveObservabilityConfig({ VITE_OTEL_ENABLED: 'true' }, local)!;
    expect(cfg.ignoreUrls.some(r => r.test('http://localhost:5173/otlp/v1/traces'))).toBe(true);
    expect(cfg.ignoreUrls.some(r => r.test('http://localhost:8080/api/cart'))).toBe(false);
  });
});
