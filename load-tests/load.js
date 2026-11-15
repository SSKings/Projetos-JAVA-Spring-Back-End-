// Teste de carga: uma taxa CONSTANTE de ações por segundo durante alguns minutos, com a mistura de uso de uma loja.
// Responde "a API aguenta X ações/s com que latência?".
//   ./load-tests/run.sh load                     # 20 ações/s por 3 min
//   RATE=100 DURATION=5m ./load-tests/run.sh load
//
// MIX=leitura (padrão): navegação e carrinho (1 requisição por ação).
// MIX=completa: inclui login (BCrypt, caro de propósito) e compra (carrinho → checkout Pix → cancelamento, que
//   devolve o estoque; 3 requisições). Precisa de mais usuários de teste: use no ambiente prod-like (rate limit alto).
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Rate } from 'k6/metrics';
import { BASE, anon, authed, json, pick, productIds, registerUsers } from './lib/common.js';

const MIX = __ENV.MIX || 'leitura';
if (!['leitura', 'completa'].includes(MIX)) throw new Error(`MIX inválido: ${MIX} (use leitura ou completa)`);
/** Compras que terminaram (checkout 201 + cancelamento 200), separadas para não sumirem no total de requisições. */
export const purchaseOk = new Rate('compra_ok');

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

/**
 * Clientes "navegando" com 1 item no carrinho: as escritas alteram a quantidade (não esgotam o estoque).
 * Na MIX=completa, também clientes "comprando" (BUYERS, padrão 100), cada um com endereço; cada VU usa sempre o mesmo
 * comprador, para duas compras simultâneas não disputarem o mesmo carrinho.
 */
export function setup() {
  const ids = productIds();
  const users = registerUsers(Number(__ENV.USERS || 10), 'carga').map((u) => {
    const res = http.post(`${BASE}/api/cart/items`, json({ productId: ids[0], quantity: 1 }), authed(u.token, 'POST /api/cart/items'));
    return { ...u, itemId: res.json('items.0.id') };
  });
  let buyers = [];
  if (MIX === 'completa') {
    buyers = registerUsers(Number(__ENV.BUYERS || 100), 'compra').map((u) => {
      const addr = http.post(`${BASE}/api/addresses`, json({
        zipCode: '01000-000', street: 'Rua de Carga', number: '1', neighborhood: 'Centro', city: 'São Paulo', state: 'SP',
      }), authed(u.token, 'POST /api/addresses'));
      return { ...u, addressId: addr.json('id') };
    });
  }
  return { ids, users, buyers };
}

/** Compra completa: põe 1 item no carrinho, fecha o pedido (Pix) e cancela (o estoque volta). */
function purchase(data, tags) {
  const b = data.buyers[(exec.vu.idInTest - 1) % data.buyers.length];
  const add = http.post(`${BASE}/api/cart/items`, json({ productId: pick(data.ids), quantity: 1 }), authed(b.token, 'POST /api/cart/items', tags));
  const order = http.post(`${BASE}/api/orders/checkout`, json({ addressId: b.addressId, paymentMethod: 'PIX' }),
    authed(b.token, 'POST /api/orders/checkout', tags));
  let cancel = null;
  if (order.status === 201) {
    cancel = http.post(`${BASE}/api/orders/${order.json('id')}/cancel`, null, authed(b.token, 'POST /api/orders/{id}/cancel', tags));
  }
  const ok = add.status === 200 && order.status === 201 && cancel !== null && cancel.status === 200;
  purchaseOk.add(ok);
  check(order, { 'compra: checkout 201': (x) => x.status === 201 });
  return ok;
}

function login(data, tags) {
  const u = pick(data.users);
  const res = http.post(`${BASE}/api/auth/login`, json({ email: u.email, password: 'Carga12345' }), anon('POST /api/auth/login', tags));
  check(res, { 'login 200': (x) => x.status === 200 });
}

/**
 * Uma ação de cliente. MIX=leitura: 1 requisição por ação ("ações/s" ≈ "requisições/s").
 * MIX=completa: 42% lista, 10% busca, 22% detalhe, 10% ver carrinho, 8% alterar carrinho, 3% login, 5% compra.
 */
export function action(data, tags = {}) {
  if (MIX === 'completa') {
    const c = Math.random();
    if (c < 0.03) return login(data, tags);
    if (c < 0.08) return purchase(data, tags);
  }
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
