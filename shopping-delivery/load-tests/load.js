// Teste de carga: uma taxa CONSTANTE de ações por segundo durante alguns minutos, com a mistura de uso de uma loja
// (navegar muito, carrinho menos). Responde "a API aguenta X ações/s com que latência?".
//   ./load-tests/run.sh load                     # 20 ações/s por 3 min
//   RATE=100 DURATION=5m ./load-tests/run.sh load
import http from 'k6/http';
import { check } from 'k6';
import { BASE, anon, authed, json, pick, productIds, registerUsers } from './lib/common.js';

const RATE = Number(__ENV.RATE || 20);
const DURATION = __ENV.DURATION || '3m';
const SLO_MS = Number(__ENV.SLO_MS || 500);

export const options = {
  scenarios: {
    carga: {
      executor: 'constant-arrival-rate',  // chegada independente da velocidade da API (como clientes reais)
      rate: RATE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: Math.max(10, RATE), maxVUs: Math.max(50, RATE * 5),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],                    // menos de 1% de erro
    http_req_duration: [`p(95)<${SLO_MS}`],            // 95% abaixo do SLO
    dropped_iterations: ['count<10'],                  // o k6 conseguiu gerar a taxa pedida
    'http_req_duration{name:GET /api/products}': [`p(95)<${SLO_MS}`],
    'http_req_duration{name:GET /api/products/{id}}': [`p(95)<${SLO_MS}`],
    'http_req_duration{name:GET /api/cart}': [`p(95)<${SLO_MS}`],
    'http_req_duration{name:PUT /api/cart/items/{itemId}}': [`p(95)<${SLO_MS}`],
  },
};

/** 10 clientes com 1 item no carrinho: as escritas alteram a quantidade (não esgotam o estoque). */
export function setup() {
  const ids = productIds();
  const users = registerUsers(Number(__ENV.USERS || 10), 'carga').map((u) => {
    const res = http.post(`${BASE}/api/cart/items`, json({ productId: ids[0], quantity: 1 }), authed(u.token, 'POST /api/cart/items'));
    return { ...u, itemId: res.json('items.0.id') };
  });
  return { ids, users };
}

/** Uma ação de cliente = uma requisição, para "ações/s" ≈ "requisições/s". */
export function action(data, tags = {}) {
  const r = Math.random();
  const u = pick(data.users);
  let res;
  if (r < 0.45) {
    res = http.get(`${BASE}/api/products?page=0&size=12`, anon('GET /api/products', tags));
  } else if (r < 0.55) {
    res = http.get(`${BASE}/api/products?search=${pick(['camiseta', 'tenis', 'livro', 'mouse'])}`, anon('GET /api/products', tags));
  } else if (r < 0.80) {
    res = http.get(`${BASE}/api/products/${pick(data.ids)}`, anon('GET /api/products/{id}', tags));
  } else if (r < 0.92) {
    res = http.get(`${BASE}/api/cart`, authed(u.token, 'GET /api/cart', tags));
  } else {
    res = http.put(`${BASE}/api/cart/items/${u.itemId}`, json({ quantity: 1 + Math.floor(Math.random() * 2) }),
      authed(u.token, 'PUT /api/cart/items/{itemId}', tags));
  }
  check(res, { 'status 2xx': (x) => x.status >= 200 && x.status < 300 });
}

export default function (data) {
  action(data);
}
