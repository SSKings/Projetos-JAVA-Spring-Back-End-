// Limite da BORDA (nginx) para um único IP: manda RATE requisições/s de um IP só e mede quantas o nginx recusa (429).
// Mostra o efeito do "limit_req zone=api rate=20r/s burst=40" de deploy/nginx.conf em clientes que compartilham IP
// (CGNAT de operadora móvel, rede de empresa, Wi-Fi público). Use contra a pilha de produção com os limites REAIS:
//   NGINX_LIMITS=prod ./load-tests/prod-like/prod-like.sh up      # regenera o nginx com os limites de produção
//   RATE=30 ./load-tests/prod-like/prod-like.sh edge
import http from 'k6/http';
import { Rate } from 'k6/metrics';
import { BASE, anon } from './lib/common.js';

const RATE = Number(__ENV.RATE || 30);
const limited = new Rate('recusadas_429');

export const options = {
  scenarios: { borda: { executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: __ENV.DURATION || '30s', preAllocatedVUs: 20 } },
  thresholds: { recusadas_429: ['rate>=0'] },
};

export default function () {
  const res = http.get(`${BASE}/api/products?page=0&size=12`, anon('GET /api/products'));
  limited.add(res.status === 429);
}

export function handleSummary(data) {
  const r = data.metrics.recusadas_429.values;
  const total = r.passes + r.fails;
  return { stdout: `\nBORDA (um IP, ${RATE} req/s): ${total} requisições, ${r.passes} recusadas com 429 (${(100 * r.rate).toFixed(1)}%)\n` };
}
