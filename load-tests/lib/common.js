// Funções comuns aos testes k6 da API (ver ../README.md).
import http from 'k6/http';
import { fail } from 'k6';

export const BASE = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
export const JSON_HEADERS = { 'Content-Type': 'application/json' };

/** Parâmetros de uma requisição autenticada; `name` agrupa URLs com id nas métricas (ex.: GET /api/products/{id}). */
export function authed(token, name, extraTags = {}) {
  return { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` }, tags: { name, ...extraTags } };
}

export function anon(name, extraTags = {}) {
  return { headers: JSON_HEADERS, tags: { name, ...extraTags } };
}

export function json(body) {
  return JSON.stringify(body);
}

/**
 * Cadastra usuários de teste e devolve os tokens. O cadastro tem rate limit de 20/min por IP (RateLimitFilter):
 * por isso no máximo 15 por execução, e os testes compartilham esses usuários entre as VUs.
 */
export function registerUsers(count, prefix) {
  // MAX_REGISTER só sobe no ambiente prod-like, onde o rate limit do teste é alto (prod-like/docker-compose.loadtest.yml)
  const max = Number(__ENV.MAX_REGISTER || 15);
  if (count > max) fail(`no máximo ${max} usuários por execução (rate limit do cadastro: 20/min por IP)`);
  const users = [];
  const stamp = Date.now();
  for (let i = 0; i < count; i++) {
    const email = `${prefix}-${stamp}-${i}@carga.teste`;
    const res = http.post(`${BASE}/api/auth/register`,
      json({ name: 'Usuário de Teste', email, password: 'Carga12345', acceptTerms: true }),
      anon('POST /api/auth/register'));
    if (res.status !== 201) {
      fail(`cadastro do usuário de teste falhou (HTTP ${res.status}): ${res.body}. ` +
           'A API está no ar? Rodou outro teste há menos de 1 minuto (rate limit)?');
    }
    users.push({ email, token: res.json('token') });
  }
  return users;
}

/** IDs dos produtos ativos (catálogo público). */
export function productIds() {
  const res = http.get(`${BASE}/api/products?page=0&size=50`, anon('GET /api/products'));
  if (res.status !== 200) fail(`catálogo indisponível (HTTP ${res.status}). A API está em ${BASE}?`);
  const ids = res.json('content').map((p) => p.id);
  if (ids.length === 0) fail('catálogo vazio');
  return ids;
}

export function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}
