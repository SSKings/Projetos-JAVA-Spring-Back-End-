// Teste de estresse em DEGRAUS: aumenta a taxa de ações/s em patamares (25, 50, 100, ...) e mede cada um.
// Responde "qual é o limite da API?": o último degrau que ainda cumpre o SLO (erro < 1%, p95 < SLO_MS e o k6
// consegue gerar a taxa pedida). No primeiro degrau muito degradado (> 20% de erro) o teste para sozinho.
//   ./load-tests/run.sh stress
//   STEPS=50,100,200,400 STEP_SECONDS=60 SLO_MS=300 ./load-tests/run.sh stress
import { action, setup as loadSetup } from './load.js';

const STEPS = (__ENV.STEPS || '25,50,100,200,400,800,1600').split(',').map(Number);
const STEP_SECONDS = Number(__ENV.STEP_SECONDS || 45);
const SLO_MS = Number(__ENV.SLO_MS || 500);
const MAX_VUS = Number(__ENV.MAX_VUS || 2000);

const scenarios = {};
const thresholds = {};
STEPS.forEach((rate, i) => {
  const name = `degrau_${String(i + 1).padStart(2, '0')}_${rate}`;
  scenarios[name] = {
    executor: 'constant-arrival-rate', exec: 'step', rate, timeUnit: '1s', duration: `${STEP_SECONDS}s`,
    startTime: `${i * STEP_SECONDS}s`, preAllocatedVUs: Math.min(MAX_VUS, Math.max(10, rate)), maxVUs: MAX_VUS,
  };
  // As métricas por degrau só aparecem no resumo se tiverem threshold; o de erro também interrompe o teste.
  // Usa a tag "scenario" (nativa): dropped_iterations não recebe tags personalizadas do cenário.
  thresholds[`http_req_duration{scenario:${name}}`] = ['p(95)>=0'];
  thresholds[`http_reqs{scenario:${name}}`] = ['count>=0'];
  thresholds[`iterations{scenario:${name}}`] = ['count>=0'];
  thresholds[`compra_ok{scenario:${name}}`] = ['rate>=0'];
  thresholds[`dropped_iterations{scenario:${name}}`] = ['count>=0'];
  thresholds[`http_req_failed{scenario:${name}}`] = [{ threshold: 'rate<0.2', abortOnFail: true, delayAbortEval: '10s' }];
});

export const options = { scenarios, thresholds, summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'] };

export function setup() {
  return loadSetup();
}

export function step(data) {
  action(data);
}

function metric(data, name, scenario) {
  const m = data.metrics[`${name}{scenario:${scenario}}`];
  return m ? m.values : null;
}

export function handleSummary(data) {
  const rows = [];
  let limit = null;
  let broken = false;
  STEPS.forEach((rate, i) => {
    const name = `degrau_${String(i + 1).padStart(2, '0')}_${rate}`;
    const reqs = metric(data, 'http_reqs', name);
    if (!reqs || reqs.count === 0) return;  // degrau não executado (teste interrompido antes)
    const iters = metric(data, 'iterations', name);
    const buys = metric(data, 'compra_ok', name);
    const dur = metric(data, 'http_req_duration', name);
    const failed = metric(data, 'http_req_failed', name);
    const dropped = metric(data, 'dropped_iterations', name);
    const achieved = (iters ? iters.count : reqs.count) / STEP_SECONDS;   // ações/s
    const reqPerSec = reqs.count / STEP_SECONDS;
    const droppedPct = dropped ? (100 * dropped.count) / (rate * STEP_SECONDS) : 0;
    const errPct = failed ? 100 * failed.rate : 0;
    // "obtido" abaixo do alvo também reprova: a API não absorveu a taxa pedida (mesmo se o descarte não for medido)
    const ok = errPct < 1 && dur['p(95)'] < SLO_MS && droppedPct < 1 && achieved >= 0.99 * rate;
    if (ok && !broken) limit = { rate, achieved, p95: dur['p(95)'] };
    if (!ok) broken = true;
    rows.push({ degrau: name, alvo: rate, obtido: achieved, req_s: reqPerSec, p50: dur['p(50)'], p95: dur['p(95)'], p99: dur['p(99)'],
                erro_pct: errPct, descartadas_pct: droppedPct, compras_ok_pct: buys && (buys.passes + buys.fails) > 0 ? 100 * buys.rate : null, ok });
  });

  const f = (n, d = 0) => (n === undefined || n === null ? '-' : n.toFixed(d));
  let out = `\nTESTE DE ESTRESSE — ${new Date().toISOString()}  (SLO: erro < 1%, p95 < ${SLO_MS} ms)\n\n`;
  out += 'ações/s: alvo  obtido   req/s   p50 ms   p95 ms   p99 ms   erro %  descart. %  compras ok  resultado\n';
  for (const r of rows) {
    out += `${f(r.alvo).padStart(13)}  ${f(r.obtido, 1).padStart(6)}  ${f(r.req_s, 0).padStart(6)}  ${f(r.p50, 1).padStart(7)}  ${f(r.p95, 1).padStart(7)}  ` +
           `${f(r.p99, 1).padStart(7)}  ${f(r.erro_pct, 2).padStart(7)}  ${f(r.descartadas_pct, 1).padStart(10)}  ` +
           `${(r.compras_ok_pct === null ? '-' : f(r.compras_ok_pct, 1) + '%').padStart(10)}  ${r.ok ? 'OK' : 'ACIMA DO LIMITE'}\n`;
  }
  out += limit
    ? `\nLIMITE: ~${f(limit.achieved)} ações/s dentro do SLO (degrau de ${limit.rate}/s, p95 ${f(limit.p95, 1)} ms).\n`
    : '\nLIMITE: nem o primeiro degrau cumpriu o SLO — comece com STEPS menores.\n';
  if (rows.length && rows.every((r) => r.ok)) out += 'Todos os degraus passaram: o limite é maior que o último. Rode com STEPS maiores.\n';
  out += '"descart. %" > 0 = o k6 não teve VUs para gerar a taxa (a API ficou lenta demais ou falta máquina).\n';
  out += 'Veja o comportamento do servidor no Grafana: dashboard "ShopEasy API — Visão geral" (latência, JVM, pool).\n';
  return {
    stdout: out,
    [`results/stress-${new Date().toISOString().replace(/[:.]/g, '-')}.json`]: JSON.stringify({ slo_ms: SLO_MS, step_seconds: STEP_SECONDS, limit, rows }, null, 2),
  };
}
