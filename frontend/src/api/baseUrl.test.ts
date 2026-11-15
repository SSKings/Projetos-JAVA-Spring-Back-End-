import { describe, expect, it } from 'vitest';
import { DEFAULT_API_PORT, resolveApiBaseUrl } from './baseUrl';
import api from './axios';

const at = (hostname: string, protocol = 'http:') => ({ hostname, protocol });

describe('resolveApiBaseUrl', () => {
  it.each([
    ['localhost', 'http://localhost:8080'],
    ['192.168.1.180', 'http://192.168.1.180:8080'],
    ['meu-pc.local', 'http://meu-pc.local:8080'],
    ['[::1]', 'http://[::1]:8080'],
  ])('sem variável usa o host da página (%s)', (hostname, expected) => {
    expect(resolveApiBaseUrl(undefined, at(hostname))).toBe(expected);
  });

  it('mantém o protocolo da página', () => {
    expect(resolveApiBaseUrl(undefined, at('loja.exemplo.com', 'https:'))).toBe('https://loja.exemplo.com:8080');
  });

  it('VITE_API_BASE_URL tem prioridade e perde barras finais', () => {
    expect(resolveApiBaseUrl('https://api.exemplo.com/', at('192.168.1.180'))).toBe('https://api.exemplo.com');
    expect(resolveApiBaseUrl('  http://10.0.0.5:9000//  ', at('localhost'))).toBe('http://10.0.0.5:9000');
  });

  it('"/" = mesma origem (atrás do proxy reverso): URLs relativas', () => {
    expect(resolveApiBaseUrl('/', at('loja.com.br', 'https:'))).toBe('');
    expect(resolveApiBaseUrl(' / ', at('loja.com.br', 'https:'))).toBe('');
  });

  it('variável vazia é ignorada', () => {
    expect(resolveApiBaseUrl('   ', at('192.168.1.180'))).toBe('http://192.168.1.180:8080');
  });

  it('a instância do axios usa o host da página atual (jsdom: localhost)', () => {
    expect(api.defaults.baseURL).toBe(`http://${window.location.hostname}:${DEFAULT_API_PORT}`);
  });
});
