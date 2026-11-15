// Simulações que DISPARAM os alertas do Grafana de propósito, para conferir dashboards e notificações (Telegram).
// Não usa contas reais: os logins são para emails inexistentes e o webhook é forjado (recusado com 401).
//   ./load-tests/run.sh alerts                     # todas, ~3 min
//   DRILL=webhook ./load-tests/run.sh alerts       # só uma: forca-bruta | varredura | webhook
//
// Alerta esperado (pasta "ShopEasy — Alertas"):  forca-bruta → "Muitos logins recusados" e "Rate limit acionado";
// varredura → "Pico de 401/403"; webhook → "Webhook Pix recusado" (na hora). Os demais disparam após a duração
// mínima (2 min) + até 1 min de avaliação. ATENÇÃO: durante a simulação, o rate limit de login (20/min por IP)
// também vale para você nesta máquina — o login pelo navegador pode responder 429 por até 1 minuto.
import http from 'k6/http';
import { check } from 'k6';
import { BASE, anon, json } from './lib/common.js';

const DRILL = __ENV.DRILL || 'todos';
const DURATION = __ENV.DURATION || '3m';
const ALL = ['forca-bruta', 'varredura', 'webhook'];
const selected = DRILL === 'todos' ? ALL : DRILL.split(',');
for (const d of selected) {
  if (!ALL.includes(d)) throw new Error(`DRILL inválido: ${d} (use ${ALL.join(', ')} ou todos)`);
}

const scenarios = {
  'forca-bruta': { executor: 'constant-arrival-rate', exec: 'bruteForce', rate: 1, timeUnit: '1s', duration: DURATION, preAllocatedVUs: 2 },
  varredura: { executor: 'constant-arrival-rate', exec: 'scan', rate: 1, timeUnit: '1s', duration: DURATION, preAllocatedVUs: 2 },
  webhook: { executor: 'shared-iterations', exec: 'forgedWebhook', vus: 1, iterations: 1 },
};

export const options = {
  scenarios: Object.fromEntries(selected.map((d) => [d, scenarios[d]])),
  // aqui "falhar" é o esperado (400/401/429): não conta como erro do teste
  thresholds: { checks: ['rate==1'] },
};

/** ~60 logins errados por minuto para 3 emails inexistentes → 400 (recusado) e 429 (bloqueio/rate limit). */
export function bruteForce() {
  const email = `alvo-${Math.floor(Math.random() * 3)}@inexistente.teste`;
  const res = http.post(`${BASE}/api/auth/login`, json({ email, password: `chute${Math.random()}` }), anon('POST /api/auth/login'));
  check(res, { 'login recusado (400) ou bloqueado (429)': (r) => r.status === 400 || r.status === 429 });
}

/** ~60 acessos sem token por minuto a rotas protegidas → 401. */
export function scan() {
  const path = ['/api/cart', '/api/orders', '/api/addresses', '/api/account'][Math.floor(Math.random() * 4)];
  const res = http.get(`${BASE}${path}`, anon(`GET ${path}`));
  check(res, { 'sem login → 401': (r) => r.status === 401 });
}

/** Uma notificação Pix sem assinatura (como um atacante tentando confirmar um pagamento) → 401. */
export function forgedWebhook() {
  const res = http.post(`${BASE}/api/payments/pix/webhook`, json({ txid: 'simulacao-alerta', endToEndId: 'E00000000', amount: 1 }),
    anon('POST /api/payments/pix/webhook'));
  check(res, { 'webhook forjado recusado → 401': (r) => r.status === 401 });
}
