# Observabilidade (OpenTelemetry + Grafana LGTM) — especificação e guia de manutenção

> **Status:** implementado (ADR-022, 2026-11-06).
>
> Este documento era a especificação inicial. Agora descreve **o que está implementado**: as regras que não podem
> quebrar, como verificar e como manter. As configurações completas ficam nos próprios arquivos (seção 3). Aqui
> fica o porquê de cada escolha, para o documento não divergir do código.
>
> Antes de mudar qualquer coisa de observabilidade, leia as seções 4 a 7 e rode a verificação da seção 8.

---

## 1. Objetivo

Seguir uma ação do usuário do navegador até a consulta SQL, num único trace, com logs e métricas correlacionados:

- **Traces:** de cada requisição do front (axios/XHR) até o controller, o repositório, o Hibernate e o JDBC no back.
- **Logs:** cada linha do back leva `trace_id`/`span_id` e abre o trace correspondente no Grafana.
- **Métricas:** HTTP, JVM, pool de conexões etc. do back no Prometheus.

**Fora do escopo (por enquanto):**
- produção, que tem pendências na seção 10;
- métricas e logs do navegador: o front envia só traces;
- envio de notificações dos alertas para fora do Grafana (os alertas existem; ver seção 4.7).

---

## 2. Arquitetura

```
Navegador (frontend-react)
  │  XHR/fetch da aplicação ──► API  (cabeçalho traceparent W3C, só para a API desta aplicação)
  │  spans (OTLP/HTTP JSON) ──► /otlp no Vite (mesma origem) ──┐
  ▼                                                            │
API Spring Boot (backend-spring) + agente Java OpenTelemetry   │
  │  traces, métricas, logs (OTLP http/protobuf) ──────────────┤
  ▼                                                            ▼
                                     OpenTelemetry Collector (:4317 gRPC / :4318 HTTP)
                                       ├─ traces  ──► Tempo        (:3200)
                                       ├─ logs    ──► Loki /otlp   (:3100)
                                       └─ métricas ─► exporter :8889 ◄── scrape ── Prometheus (:9090)
                                                                                      │
                     Grafana (:3000) ── datasources com uid fixo: prometheus, tempo, loki
```

| Nome do serviço (`service.name`) | Quem define |
|---|---|
| `frontend-react` | `DEFAULT_SERVICE_NAME` em `src/observability/config.ts` (ou `VITE_OTEL_SERVICE_NAME`) |
| `backend-spring` | `OTEL_SERVICE_NAME` em `scripts/run-backend.sh` |

| Porta (sempre em `127.0.0.1`) | Serviço |
|---|---|
| 3000 | Grafana (sem login: admin anônimo) |
| 3100 | Loki |
| 3200 | Tempo |
| 4317 / 4318 | Collector OTLP gRPC / HTTP |
| 9090 | Prometheus |
| 8889 | Exporter Prometheus do collector (só na rede Docker, não publicada) |

---

## 3. Mapa de arquivos

| Arquivo | Papel |
|---|---|
| `shopping-delivery/observability-stack/docker-compose.yml` | Collector, Prometheus, Tempo, Loki e Grafana (versões fixas, portas em 127.0.0.1) |
| `shopping-delivery/observability-stack/otel-config.yaml` | Pipelines do collector: traces → Tempo, logs → Loki, métricas → exporter Prometheus |
| `shopping-delivery/observability-stack/prometheus.yml` | Scrape do collector (`otel-collector:8889`) |
| `shopping-delivery/observability-stack/tempo-config.yaml` | Tempo local (retenção de 1h) |
| `shopping-delivery/observability-stack/grafana-datasources.yaml` | Datasources e correlações (trace ↔ log, trace → métricas) |
| `shopping-delivery/observability-stack/grafana-dashboards.yaml` | Provider que carrega `dashboards/` na pasta "ShopEasy" do Grafana |
| `shopping-delivery/observability-stack/dashboards/*.json` | Dashboards versionados: `shopeasy-api.json` (home) e `shopeasy-business.json` |
| `shopping-delivery/observability-stack/alerting/shopeasy-alerts.json` | 12 regras de alerta da API (pasta "ShopEasy — Alertas") |
| `shopping-delivery/scripts/test_grafana_provisioning.py` | Validação de dashboards e alertas no CI (seção 8.1) |
| `shopping-delivery/observability-stack/README.md` | Uso do dia a dia e tabela de desvios desta especificação |
| `shopping-delivery/scripts/run-backend.sh` | Baixa o agente (versão do pom, checksum verificado), gera o jar e sobe a API com o agente |
| `shopping-delivery/shopping-delivery/pom.xml` | `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, propriedade `otel-javaagent.version` |
| `shopping-delivery/shopping-delivery/src/main/resources/application.yml` | `management.*` (Actuator) e `logging.pattern.level` com `trace_id`/`span_id` |
| `…/config/SecurityConfig.java` | Regras de acesso do `/actuator/**` |
| `…/config/CorsConfig.java` | `ALLOWED_HEADERS` com `traceparent` e `tracestate` |
| `shopping-delivery-frontend/src/observability/config.ts` | `resolveObservabilityConfig()`: liga/desliga, exporter, para onde vai o `traceparent` |
| `shopping-delivery-frontend/src/observability/instrumentation.ts` | `initObservability()`: provider, `BatchSpanProcessor`, instrumentações XHR + fetch |
| `shopping-delivery-frontend/src/main.tsx` | Import dinâmico da instrumentação **antes** do render, só quando ligada |
| `shopping-delivery-frontend/vite.config.ts` | Proxy `/otlp` → collector (`OTEL_COLLECTOR_URL`, padrão `http://localhost:4318`) |
| `shopping-delivery-frontend/.env.example` | Variáveis `VITE_OTEL_*` comentadas |
| `docs/adr.md` (ADR-022), `docs/security-review.md` (seção 7) | Decisão e análise de risco |

---

## 4. Infraestrutura — regras de cada arquivo

### 4.1 `docker-compose.yml`
- **Versões fixas, nunca `:latest`.** Versões atuais:
  - `otel/opentelemetry-collector-contrib:0.163.2`
  - `prom/prometheus:v3.15.1`
  - `grafana/tempo:2.10.10`
  - `grafana/loki:3.7.9`
  - `grafana/grafana:12.4.13`

  O Dependabot **não** cobre este arquivo; a atualização é manual (seção 9.1).
- **Toda porta publicada como `127.0.0.1:<porta>:<porta>`.** O Grafana dá papel Admin a qualquer visitante
  (`GF_AUTH_ANONYMOUS_ORG_ROLE=Admin`). Publicar em `0.0.0.0` expõe isso para a rede.
- A imagem do Loki usa a configuração embutida (`/etc/loki/local-config.yaml`), que já aceita OTLP e structured
  metadata. Se um dia for preciso um arquivo próprio, mantenha `allow_structured_metadata: true`.
- Sem volumes de dados: `docker compose down` apaga traces, logs e métricas. Isso é intencional, porque a stack é de dev.

### 4.2 `otel-config.yaml` (collector)
- **Três pipelines: `traces`, `metrics` e `logs`.** A especificação original não tinha o de logs; os logs do back
  chegavam ao collector e eram descartados.
- **Logs vão para o Loki por `otlphttp` em `http://loki:3100/otlp`.** É o OTLP nativo do Loki 3.x. Não use o
  exporter `loki`, que foi removido do collector, nem `otlp` (gRPC), que o Loki não aceita.
- `memory_limiter` vem antes de `batch` em todos os pipelines.
- `resource_to_telemetry_conversion` no exporter Prometheus faz `service.name` virar o label `service_name` das métricas.
  **Exceção:** `target_info` não recebe `service_name` (o serviço aparece como `exported_job`). Para saber se a API
  está reportando, use uma métrica dela, como `jvm_cpu_count{service_name="backend-spring"}`.
- `metric_expiration: 1m` no exporter Prometheus: uma série some 1 minuto depois de parar de chegar (padrão: 5 min).
  É o que faz o alerta "API sem telemetria" disparar em cerca de 2 minutos. Não aumente sem rever esse alerta.
- O bloco `cors` do receiver HTTP só vale para quem apontar o exporter do front direto para `:4318`
  (`VITE_OTEL_EXPORTER_URL`). O caminho padrão é o proxy `/otlp` (mesma origem, sem CORS). Se você trocar a porta
  do Vite, atualize essas origens.

### 4.3 `tempo-config.yaml`
- A chave é **`block_retention`**, toda em minúsculas. Com `block_Retention`, como estava na especificação, o Tempo
  não sobe.
- Os receivers OTLP do Tempo escutam só dentro da rede Docker; quem envia é o collector (`tempo:4317`).

### 4.4 `grafana-datasources.yaml`
- **Os uids são fixos e fazem parte do contrato:** `prometheus`, `tempo`, `loki`. As correlações usam o uid, não o
  nome. Sem uid fixo, o Grafana gera uids aleatórios e os links somem.
- **Trace → logs:** `tracesToLogsV2` com `filterByTraceId: true` e a tag `service.name` → `service_name`.
- **Log → trace:** `derivedFields` com `matcherType: label` e `matcherRegex: trace_id`. Logs que chegam por OTLP
  guardam `trace_id` como *structured metadata*, não no texto da linha. Por isso o regex sobre o texto, como na
  especificação original, não encontrava nada.
- `$${__value.raw}`: o `$$` escapa a interpolação de variáveis de ambiente do provisioning do Grafana.

### 4.5 `prometheus.yml`
- Um único job, que faz scrape do collector. O Prometheus **não** lê o `/actuator/prometheus` da API: as métricas
  da API chegam por OTLP pelo agente. O `/actuator/prometheus` existe para consulta pontual de um ADMIN (seção 5.2).

### 4.6 Dashboards (`grafana-dashboards.yaml` + `dashboards/`)

Os dashboards são provisionados a partir de arquivos (pasta **ShopEasy** no Grafana). O "Visão geral" é a home
(`GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH`).

**`shopeasy-api` — ShopEasy API — Visão geral.** Tem a variável **Rota** para filtrar uma ou várias rotas.

| Seção | Painéis | Métricas / fonte |
|---|---|---|
| Visão geral | API reportando, req/s, % 5xx, % 4xx, latência p95, conexões em uso | `target_info`, `http_server_request_duration_seconds_*`, `db_client_connections_usage` |
| Tráfego (RED) | req/s por rota, respostas por status, latência p50/p95/p99, p95 por rota, tabela de rotas do período | `http_server_request_duration_seconds_*` |
| Segurança | Recusas 401/403/413/429 por rota; login por resultado | idem, filtrado por status / `/api/auth/login` |
| JVM | heap por pool + committed, non-heap, CPU, GC, threads, classes | `jvm_*` |
| Banco (HikariCP) | conexões used/idle/máximo, fila, espera/uso p95 | `db_client_connections_*` |
| Logs e traces | logs error/warn (Loki); traces lentos (> 500 ms) e com erro (Tempo, TraceQL) | Loki, Tempo |

**`shopeasy-business` — ShopEasy — Negócio e segurança.** São indicadores derivados das métricas HTTP, sem
código novo no backend:
- totais do período: pedidos criados, checkouts recusados, Pix confirmados e recusados no webhook, cadastros,
  logins recusados ou bloqueados;
- por minuto: checkouts, webhook Pix, polling de pagamento, cancelamentos, devoluções e estornos, login por
  resultado, cadastros e carrinho;
- área admin: ações do admin e acessos negados.

**Regras:**
- **A fonte é o JSON.** Dashboards provisionados podem ser editados no Grafana (`allowUiUpdates: true`), mas a
  edição some quando o container é recriado. Para manter uma alteração:
  1. no Grafana, use **Export → Export as JSON** com *Export for sharing externally* desligado;
  2. salve o JSON por cima do arquivo em `dashboards/`, mantendo `uid` e `id`;
  3. rode o teste da seção 8.1 e faça o commit.
- **Datasources sempre por uid** (`prometheus`, `tempo`, `loki`), nunca pelo nome.
- **Contrato das rotas:** `http_route` é o *template* da rota no Spring (`/api/orders/{id}/payment`), não a URL.
  Renomear um endpoint zera os painéis que o citam. O teste da seção 8.1 compara as rotas citadas com os
  controllers e falha nesse caso.
- **Status de login:** senha errada, conta desativada e MFA respondem **400** (`BusinessException`), não 401;
  bloqueio por tentativas ou rate limit responde **429**. Os painéis de login usam essa semântica. Se o
  `AuthService` mudar, ajuste os painéis.
- **Webhook Pix:** 401 = assinatura inválida ou replay; 403 = IP fora da allowlist; 400 = payload ou valor inválido;
  429 = rate limit.
- **"Requisições com erro" (Tempo)** usa `status = error`. Pela convenção do OpenTelemetry, só 5xx e exceções marcam
  o span do servidor como erro. Os 4xx aparecem nos painéis de segurança.
- Painéis vazios são normais quando o evento não aconteceu no período (nenhum 5xx, nenhum estorno). "SEM DADOS" em
  *API reportando* indica API fora do ar ou rodando sem o agente.

---

### 4.7 Alertas (`alerting/shopeasy-alerts.json`)

São 12 regras do Grafana Alerting, provisionadas de arquivo e avaliadas a cada minuto, na pasta
**ShopEasy — Alertas** (*Alerting → Alert rules*). Cada uma tem `summary`, `description` (com o valor medido),
`o_que_fazer` e link para o painel correspondente do dashboard.

| Grupo | Alerta | Condição | Dura | Severidade |
|---|---|---|---|---|
| Disponibilidade | API sem telemetria (fora do ar?) | nenhuma instância com `jvm_cpu_count` (sem dados também dispara) | 1m | critical |
| | Taxa de erros 5xx acima de 5% | % de 5xx em 5 min > 5 | 5m | critical |
| | Latência p95 acima de 1 s | p95 em 5 min, sem `/actuator` | 5m | warning |
| | Requisições esperando conexão do banco | fila do HikariCP > 0 | 2m | critical |
| Segurança | Pico de 401/403 (possível varredura) | > 30/min | 2m | warning |
| | Muitos logins recusados (possível força bruta) | 400 ou 429 em `/api/auth/login` > 10/min | 2m | warning |
| | Rate limit acionado com frequência | 429 > 5/min | 2m | warning |
| | Webhook Pix recusado | qualquer 4xx no webhook nos últimos 10 min | imediato | warning |
| Recursos | Pool de conexões acima de 90% | usadas / máximo > 0,9 | 5m | warning |
| | Memória heap alta depois do GC | G1 Old Gen após GC > 85% do limite | 10m | warning |
| | Tempo excessivo em garbage collection | > 10% do tempo | 5m | warning |
| | CPU da JVM acima de 90% | `jvm_cpu_recent_utilization_ratio` > 0,9 | 10m | warning |

**Regras:**
- **Pasta própria, nunca "ShopEasy".** O provisionamento de alertas roda antes do de dashboards. Com o mesmo nome,
  ele criava uma segunda pasta "ShopEasy" (uid aleatório) ao lado da dos dashboards. O CI impede isso.
- **Sem dados = OK**, exceto "API sem telemetria". Sem tráfego, não há taxa de erro nem latência a avaliar.
- **Webhook Pix:** a consulta é `atual - (10 min atrás or 0)`, e não `increase()`. O contador de cada combinação
  rota/status nasce com valor 1, e `increase()` só enxerga a diferença entre duas amostras: a primeira recusa
  após o start da API passaria em branco.
- **Templates:** só funções do Grafana (`printf`, `humanize`, `humanizePercentage`…). Uma função inexistente, como
  `mul`, faz a descrição chegar com o template cru. O CI confere a lista.
- **Heap:** a regra usa o pool `G1 Old Gen`, do G1, o coletor padrão da JVM 17. Se trocar de coletor, ajuste o
  nome do pool.
- **Mesma fonte do JSON:** alterar uma regra provisionada pela interface não é permitido. Edite o arquivo, rode o
  teste da seção 8.1 e recrie o Grafana (`docker compose up -d --force-recreate grafana`).
- **Notificações:** a política padrão do Grafana não tem destino configurado. Os alertas aparecem em *Alerting*
  (e com estado nos painéis), mas **não são enviados para fora**. Para receber, crie um *contact point*
  (email/SMTP, Telegram, Discord, Slack, webhook) e associe-o à política padrão. Pendência na seção 10.

**Testados** contra a API real (2026-11-07):
- dispararam: força bruta (33 recusas/min), rate limit (29 respostas 429/min), varredura (65 respostas 401/min) e
  webhook Pix forjado (401);
- os demais ficaram inativos com a API saudável;
- "API sem telemetria" foi conferido pela consulta: 1 com a API no ar, 0 sem ela.

## 5. Backend — regras

### 5.1 Agente Java (`scripts/run-backend.sh`)
- **A versão vem do pom** (`<otel-javaagent.version>2.33.0</otel-javaagent.version>`). O jar é baixado pelo Maven do
  Maven Central com `-C` (`--strict-checksums`), em `target/otel/`.
  - **Nunca** volte a baixar `releases/latest`: o código do agente roda dentro da JVM, com acesso a tudo.
  - O agente não é dependência da aplicação e não entra no jar.
- **Protocolo:** `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318` com `OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf`,
  que é o padrão do agente 2.x. Para usar gRPC, troque **os dois** (`grpc` + `:4317`).
- **Variáveis padrão**, todas sobrescrevíveis pelo ambiente:

  | Variável | Valor |
  |---|---|
  | `OTEL_SERVICE_NAME` | `backend-spring` |
  | `OTEL_TRACES_EXPORTER`, `OTEL_METRICS_EXPORTER`, `OTEL_LOGS_EXPORTER` | `otlp` |
  | `OTEL_METRIC_EXPORT_INTERVAL` | `15000` |
  | `OTEL_RESOURCE_ATTRIBUTES` | `deployment.environment.name=<perfil>` |

- **Opções do script:**
  - `SKIP_BUILD=1` reaproveita o jar;
  - argumentos extras vão para a aplicação (`./scripts/run-backend.sh --server.port=8081`);
  - o build usa `-DskipTests` (os testes rodam no CI e em `./mvnw verify`).
- **A aplicação não depende do agente.** `./mvnw spring-boot:run` continua funcionando; só não envia telemetria.
  Não adicione APIs do OpenTelemetry ao código de negócio sem um ADR. Se precisar de spans manuais, use
  `io.opentelemetry:opentelemetry-api` (provida pelo agente em runtime) ou a Observation API do Micrometer.
- Se o collector estiver fora do ar, o agente registra `Failed to export …` e segue. A API não para, e os dados
  desse intervalo se perdem.

### 5.2 Actuator (`application.yml` + `SecurityConfig`)

| Endpoint | Anônimo | Cliente | ADMIN |
|---|---|---|---|
| `GET /actuator/health` | 200, só `{"status":"UP"}` | 200, só status | 200 com `components` (db, disco…) |
| `/actuator/metrics/**`, `/actuator/prometheus` | 401 | 403 | 200 |
| Qualquer outro (`env`, `beans`, `heapdump`, `loggers`…) | — | — | **404** (não exposto) |

- `management.endpoints.web.exposure.include: health,metrics,prometheus`. **Não** adicione `env`, `heapdump`,
  `loggers`, `threaddump` ou `configprops`: eles revelam segredos ou permitem alterar a aplicação em runtime.
- `show-details: when-authorized` com `roles: ADMIN`. **Nunca** `always`.
- Um ADMIN sem MFA, quando o MFA é exigido (`require-admin-mfa`), não recebe `ROLE_ADMIN` do `JwtAuthFilter`. Logo,
  também não lê métricas. A regra vem de graça; não crie exceção.
- Em produção o nginx só repassa `/api/`, então o `/actuator` não é alcançável de fora. Se um health check externo
  for necessário, exponha **só** `/actuator/health` no nginx.

### 5.3 CORS (`CorsConfig.ALLOWED_HEADERS`)
- Precisa conter `traceparent` e `tracestate`. Sem eles o navegador recusa o preflight e **todas** as chamadas do
  front com a instrumentação ligada falham ("Não foi possível conectar ao servidor").
- Não troque a lista por `*`: o teste de cabeçalho desconhecido (→ 403) existe para impedir isso.

### 5.4 Logs
- `logging.pattern.level: "%5p [trace_id=%X{trace_id:-} span_id=%X{span_id:-}]"`. Quem preenche o MDC é o agente.
  Sem o agente, os campos ficam vazios, e isso é normal.
- Os logs vão para o Loki pelo agente (OTLP), não pelo console. O padrão acima é só para quem lê o terminal.
- **Não logue dados pessoais nem segredos**: tudo o que é logado vai para o Loki (ver seção 7).

---

## 6. Frontend — regras

### 6.1 Liga/desliga
- **Desligada por padrão.** Liga só com `VITE_OTEL_ENABLED=true` (sem diferenciar maiúsculas; `1`/`yes` não valem).
  Sem collector, o navegador ficaria tentando enviar spans à toa.
- `main.tsx` resolve a configuração e, se estiver ligada, faz `await import('./observability/instrumentation')`
  **antes** do `createRoot(...).render`. Assim o SDK fica num chunk separado (cerca de 25 KB gzip), que o bundle
  principal não carrega, e a instrumentação já está ativa quando a primeira requisição sai.
- Uma falha ao iniciar é capturada e vira `console.warn`. A telemetria nunca pode impedir a loja de abrir.

### 6.2 `config.ts` (função pura e testada)

| Campo | Regra |
|---|---|
| `exporterUrl` | `VITE_OTEL_EXPORTER_URL` ou `/otlp/v1/traces`, resolvida contra `location.origin` (URL absoluta) |
| `propagateTraceHeaderCorsUrls` | **Só a API desta aplicação**: a mesma regra de `resolveApiBaseUrl()` (`VITE_API_BASE_URL`, ou `<host da página>:8080`, ou a própria origem com `VITE_API_BASE_URL=/`). A regex é escapada e ancorada (`^origem(/|$)`) |
| `ignoreUrls` | A URL do exporter, para que o envio de spans não vire span (o que causaria um loop) |
| `serviceName` | `VITE_OTEL_SERVICE_NAME` ou `frontend-react` |

Não volte para regex soltas como `/localhost:8080/`: elas não funcionam pelo IP da rede (ADR-015) e casam com
qualquer URL que contenha o texto, o que vazaria o `traceparent` para terceiros.

### 6.3 `instrumentation.ts` (SDK 2.x)
- `new WebTracerProvider({ resource: resourceFromAttributes(...), spanProcessors: [new BatchSpanProcessor(exporter)] })`.
  - O SDK 2.x **removeu** `new Resource(...)` e `provider.addSpanProcessor(...)`, que a especificação original usava.
  - `SimpleSpanProcessor` faz um envio HTTP por span; não use no navegador.
- **`XMLHttpRequestInstrumentation` é obrigatória**: o axios usa XHR. `FetchInstrumentation` fica para eventuais `fetch`.
- `initObservability(config, exporter?)` aceita um exporter (os testes passam `InMemorySpanExporter`) e devolve
  `{ provider, shutdown }`.

### 6.4 Proxy `/otlp` (`vite.config.ts`)
- `'/otlp' → OTEL_COLLECTOR_URL ?? 'http://localhost:4318'`, removendo o prefixo `/otlp`. Só existe no
  `npm run dev`. `vite preview` e o build de produção não têm proxy.
- Como o Vite escuta em todas as interfaces (ADR-015), outras máquinas da rede também conseguem enviar spans por
  ele. Em dev isso é aceitável.

### 6.5 Dependências (todas com versão **exata** no `package.json`)

| Pacote | Versão | Grupo |
|---|---|---|
| `@opentelemetry/api` | 1.9.0 | API estável 1.x |
| `@opentelemetry/sdk-trace-web`, `@opentelemetry/resources` | 2.12.0 | core 2.x |
| `@opentelemetry/semantic-conventions` | 1.43.0 | convenções |
| `@opentelemetry/instrumentation`, `-instrumentation-fetch`, `-instrumentation-xml-http-request`, `-exporter-trace-otlp-http` | 0.223.0 | experimental 0.2xx |

Os pacotes **core 2.N** e **experimental 0.2NN** são publicados juntos e precisam andar juntos. Atualize todos
os `@opentelemetry/*` no mesmo PR (seção 9.2).

---

## 7. Segurança e privacidade — invariantes

1. Portas da stack só em `127.0.0.1`; Grafana anônimo só nessas condições.
2. Versões fixas para imagens, agente e pacotes npm; o agente sempre com checksum.
3. Actuator conforme a tabela da seção 5.2.
4. `traceparent` só para a API da aplicação.
5. Nada de corpo de requisição, `Authorization`, senha, token, CPF ou dados de cartão em atributos de span ou em
   logs. As instrumentações padrão não capturam corpo nem cabeçalhos. Se você ligar a captura de cabeçalhos no
   agente (`otel.instrumentation.http.server.capture-request-headers`), **nunca** inclua `Authorization`.
6. **Em dev a senha do admin gerada no start aparece no log e, portanto, no Loki (ADR-021).** Não use esta stack
   com dados reais. Antes de qualquer uso fora de dev, revise o que é logado (LGPD, art. 6º, minimização).
7. A CSP de produção (`connect-src 'self'` em `deploy/nginx.conf`) impede o front de enviar telemetria para outra
   origem. Em produção, o exporter precisa de um caminho na mesma origem atrás do nginx.

---

## 8. Verificação

### 8.1 Testes automáticos (rodam no CI)

| Teste | Garante |
|---|---|
| `ActuatorSecurityIntegrationTest` (back) | Tabela da seção 5.2. Usa `@AutoConfigureObservability`, porque o `@SpringBootTest` desliga os exportadores de métricas e sem isso o `/actuator/prometheus` responde 404 |
| `CorsPreflightTest` (back) | Preflight com `traceparent`/`tracestate` → 200; cabeçalho desconhecido → 403 |
| `src/observability/config.test.ts` (front) | Opt-in, URL do exporter, propagação só para a API (IP da rede, mesma origem, outro domínio, hosts parecidos recusados), `ignoreUrls` |
| `src/observability/instrumentation.test.ts` (front) | `service.name` no resource; XHR para a API recebe `traceparent` W3C; XHR para terceiros e sem instrumentação não recebem |
| `scripts/test_grafana_provisioning.py` (CI) | **Dashboards:** JSON e uids válidos; datasources existentes; painéis na grade sem sobreposição; `refId` únicos; só `backend-spring`; **toda `http_route` citada existe nos controllers**; `target_info` sem filtro por `service_name`; links; montagem e home no compose. **Alertas:** uids únicos; pasta diferente da dos dashboards; condição e `refId` coerentes; datasources; severidade e duração; "API sem telemetria" dispara sem dados; annotations; **só funções de template existentes**; links para painéis existentes; rotas; montagem no compose |

```bash
cd shopping-delivery/shopping-delivery && ./mvnw verify
cd shopping-delivery-frontend && npx tsc -b && npm run lint && npm run coverage && npm run build
python3 -m unittest discover -s shopping-delivery/scripts -p 'test_grafana_provisioning.py'
```

### 8.2 Verificação ponta a ponta (manual, depois de mudar a stack, o agente ou o SDK)

Este roteiro não precisa de navegador: ele reproduz o que o front faz. Rode a partir de `shopping-delivery/`.

```bash
# 1. Stack
(cd observability-stack && docker compose up -d)
until docker logs otel-collector 2>&1 | grep -q "Everything is ready"; do sleep 1; done

# 2. API com o agente (porta 8081 para não conflitar com uma API já rodando)
./scripts/run-backend.sh --server.port=8081 &

# 3. Front com a instrumentação ligada (porta 5174)
(cd ../shopping-delivery-frontend && VITE_OTEL_ENABLED=true VITE_API_BASE_URL=http://localhost:8081 npx vite --port 5174) &
until curl -s localhost:8081/actuator/health | grep -q UP; do sleep 2; done   # build + start levam ~1 min
until curl -s -o /dev/null localhost:5174; do sleep 1; done

# 4. Um span "frontend-react" pelo proxy /otlp + a chamada à API com o mesmo traceparent
TID=$(openssl rand -hex 16); SID=$(openssl rand -hex 8); NOW=$(date +%s%N)
curl -s -o /dev/null -w "proxy /otlp: %{http_code}\n" -X POST -H 'Content-Type: application/json' \
  localhost:5174/otlp/v1/traces -d '{"resourceSpans":[{"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"frontend-react"}}]},"scopeSpans":[{"spans":[{"traceId":"'$TID'","spanId":"'$SID'","name":"GET","kind":3,"startTimeUnixNano":"'$NOW'","endTimeUnixNano":"'$((NOW+50000000))'"}]}]}]}'
curl -s -o /dev/null -w "API: %{http_code}\n" -H "traceparent: 00-$TID-$SID-01" localhost:8081/api/products
# métricas: o agente exporta a cada 15 s e o Prometheus coleta a cada 15 s — espera até aparecerem (≤ ~1 min)
until curl -s localhost:9090/api/v1/query --data-urlencode 'query=http_server_request_duration_seconds_count{service_name="backend-spring"}' \
  | grep -q '"value"'; do sleep 5; done

# 5. Conferir
curl -s localhost:3200/api/traces/$TID | grep -o '"stringValue":"\(frontend-react\|backend-spring\)"' | sort -u   # os dois serviços
curl -sG localhost:3100/loki/api/v1/query_range --data-urlencode 'query={service_name="backend-spring"}' | head -c 300; echo
curl -s localhost:9090/api/v1/query --data-urlencode 'query=count by (service_name) (http_server_request_duration_seconds_count)'; echo
for u in prometheus tempo loki; do curl -s localhost:3000/api/datasources/uid/$u/health; echo; done
curl -s -o /dev/null -w "actuator/prometheus anônimo: %{http_code}\n" localhost:8081/actuator/prometheus   # 401
ss -ltn | grep -E ':(3000|3100|3200|4317|4318|9090) '                                                        # só 127.0.0.1

# 6. Limpar: encerre a API e o Vite pelos PIDs (kill %1 %2) e derrube a stack
(cd observability-stack && docker compose down)
```

**Resultado esperado:**
- proxy `/otlp` responde 200;
- o trace no Tempo tem `frontend-react` **e** `backend-spring`;
- o Loki devolve linhas do back;
- o Prometheus mostra `service_name="backend-spring"`;
- os três datasources respondem OK;
- o `/actuator/prometheus` anônimo responde 401;
- todas as portas estão em `127.0.0.1`.

**No Grafana** (`http://localhost:3000`), confira também:
- **Explore → Tempo:** o trace aparece com os dois serviços;
- **Explore → Loki:** o link *TraceID* de uma linha abre o trace;
- num span do Tempo, *Logs for this span* leva ao Loki.

---

## 9. Manutenção

### 9.1 Atualizar as imagens da stack (manual; o Dependabot não cobre o compose)
1. Veja a última versão estável de cada imagem (Docker Hub / releases).
2. Atualize **uma família por vez** em `docker-compose.yml`.
3. Leia as *breaking changes*. Pontos sensíveis:
   - **collector:** exporters renomeados ou removidos;
   - **Loki:** caminho `/otlp` e structured metadata;
   - **Tempo:** chaves de configuração;
   - **Grafana:** formato do provisioning.
4. Rode a seção 8.2.

### 9.2 Atualizar o OpenTelemetry do front
- Atualize **todos** os `@opentelemetry/*` juntos, mantendo os pares core 2.N / experimental 0.2NN da mesma
  release (seção 6.5), sempre com `npm install --save-exact`.
- O Dependabot agrupa minor/patch do npm. Num PR dele, confira se os pacotes OTel vieram juntos.
- Depois: `npm audit --omit=dev --audit-level=high`, os testes de `src/observability/`, o build e a seção 8.2.
- Major (SDK 3.x, API 2.x) é ignorada pelo Dependabot e exige migração planejada, como no ADR-020.

### 9.3 Atualizar o agente Java
1. Troque `otel-javaagent.version` no `pom.xml`. O Dependabot não atualiza, porque é uma propriedade e não uma dependência.
2. Apague `target/otel/` e rode `./scripts/run-backend.sh`. O Maven baixa e verifica o novo jar.
3. Confira o log `opentelemetry-javaagent - version: <nova>` e rode a seção 8.2.
4. Em major do agente (3.x), revise nomes de métricas e atributos: dashboards e consultas podem quebrar.

### 9.4 Mudanças comuns

| Quero… | Faça |
|---|---|
| Mudar a porta do Vite | `vite.config.ts` e as origens do `cors` em `otel-config.yaml` |
| Mudar a porta da API | Nada de observabilidade muda no back. No front, `resolveApiBaseUrl` já define para onde vai o `traceparent` |
| Instrumentar outro serviço | Use o mesmo collector (`localhost:4318`) com um `service.name` próprio. O Grafana correlaciona pelo `service_name` |
| Span manual no back | Micrometer `Observation` ou `@WithSpan` (`opentelemetry-instrumentation-annotations`) com ADR. Sem atributos com dados pessoais |
| Medir carregamento de página / cliques | `@opentelemetry/instrumentation-document-load` / `-user-interaction` na lista de `initObservability`, com teste |
| Amostrar menos traces | Back: `OTEL_TRACES_SAMPLER=parentbased_traceidratio` + `OTEL_TRACES_SAMPLER_ARG`. Front: `sampler` no `WebTracerProvider`. Com `parentbased`, o back segue a decisão do front |
| Guardar dados entre reinícios | Volumes nomeados para `/var/tempo`, `/loki`, `/prometheus` e `/var/lib/grafana` no compose |
| Novo painel / dashboard | Crie no Grafana, exporte o JSON para `dashboards/` (seção 4.6) e rode o teste. Novo arquivo = `uid` novo e único |
| Endpoint novo ou renomeado | Atualize os `http_route="..."` dos dashboards e dos alertas. O CI acusa rota inexistente |
| Novo alerta | Acrescente em `alerting/shopeasy-alerts.json` (uid único, `condition` C sobre a consulta A, annotations e link para um painel), rode o teste e recrie o Grafana |
| Receber notificações | Crie um contact point (UI ou arquivo em `alerting/`, com segredo vindo de variável de ambiente, nunca versionado) e associe-o à política padrão |

### 9.5 Problemas conhecidos

| Sintoma | Causa / solução |
|---|---|
| `Failed to export … The request could not be executed` no log da API | Collector fora do ar ou ainda subindo. Para sozinho quando o collector fica pronto; os dados desse intervalo se perdem |
| Tempo não sobe | Chave inválida em `tempo-config.yaml` (ex.: `block_Retention`). Veja `docker logs tempo` |
| Traces do back sem o span do front | Instrumentação desligada (`VITE_OTEL_ENABLED`), ou o `traceparent` não foi enviado: URL da API fora de `propagateTraceHeaderCorsUrls`, ou CORS sem `traceparent` |
| Todas as chamadas do front falham com a instrumentação ligada | CORS da API sem `traceparent`/`tracestate` (seção 5.3) |
| Loki sem logs | Pipeline `logs` ausente ou exporter errado (seção 4.2); API rodando sem o agente; logs gerados antes de o collector subir |
| Link *TraceID* no Loki não aparece | `derivedFields` sem `matcherType: label`, ou uid do Tempo diferente de `tempo` |
| `/actuator/prometheus` → 404 num teste | Falta `@AutoConfigureObservability` no teste |
| `[trace_id= span_id=]` vazio no console | API sem o agente (`./mvnw spring-boot:run`). Esperado |
| `docker compose pull` falha com `connection reset by peer` | Rede instável no download; repita o pull |
| `tsc`: `ImportMetaEnv has no properties in common with ObservabilityEnv` | `ObservabilityEnv` precisa da index signature `[key: string]: unknown` |
| "API reportando" em 0 / SEM DADOS com a API no ar | Consulta em `target_info{service_name=...}`: esse label não existe em `target_info` (seção 4.2) |
| Duas pastas "ShopEasy" no Grafana | Alertas provisionados na mesma pasta dos dashboards (seção 4.7) |
| Descrição do alerta com `{{ ... }}` cru | Função de template inexistente (ex.: `mul`) |
| Contadores de 1 em 1 (ex.: "Pedidos criados") menores que o real por 1 | `increase()` ignora o primeiro evento de cada série nova (o contador nasce em 1). Em períodos longos é desprezível; em alertas de evento único, use `atual - (offset or 0)` (seção 4.7) |

---

## 10. Pendências para produção

1. **Backend de telemetria autenticado** (Grafana Cloud, ou a stack com login, TLS e sem acesso anônimo). Nada da
   `observability-stack/` vai para produção como está.
2. **Collector atrás do nginx**, num caminho da mesma origem (ex.: `/otlp/`), com limite de taxa e de tamanho, para
   o front exportar sem mudar a CSP.
3. **Revisão LGPD dos logs:**
   - remover a senha de admin do log, que em produção já vem de `ADMIN_PASSWORD`;
   - nenhum dado pessoal em logs nem spans;
   - retenção definida.
4. **Volumes persistentes e retenção** adequados (Tempo, Loki, Prometheus).
5. **Notificação dos alertas** (as regras existem, seção 4.7):
   - um contact point real (email, Telegram ou outro) com o segredo vindo do ambiente;
   - um alerta para pedidos em `REFUND_PENDING`, que pede uma métrica de negócio no backend: hoje só há métricas
     HTTP.
6. **Amostragem** para controlar volume e custo (seção 9.4).
7. Avaliar o tipo `docker-compose` no Dependabot para as imagens da stack.

---

## 11. Histórico — desvios em relação à especificação original

A primeira versão deste documento (pedido inicial) foi seguida na arquitetura. Os pontos abaixo foram corrigidos
na implementação, todos verificados com a stack rodando.

| Original | Problema | Implementado |
|---|---|---|
| Imagens `:latest`; agente de `releases/latest` sem verificação | Não reproduzível; risco de cadeia de suprimentos | Versões fixas; agente pelo Maven com checksum |
| Portas `0.0.0.0` e Grafana anônimo Admin | Stack aberta para a rede | `127.0.0.1` |
| `version: '3.8'` no compose | Campo obsoleto no Compose v2 | Removido; `name: shopeasy-observability` |
| Exporter `otlp/loki` (gRPC) sem pipeline de logs | Logs descartados | `otlphttp/loki` → `/otlp` + pipeline `logs` |
| `block_Retention` | Tempo não sobe | `block_retention` |
| Agente em `:4317` | O agente 2.x usa http/protobuf (4318) | `:4318` + protocolo explícito |
| Datasources sem `uid`; derived field por regex no texto | Correlação quebrada | uids fixos; match por label `trace_id` |
| `application.properties`, `show-details=always` | O projeto usa YAML; detalhes públicos | `application.yml`, `when-authorized` + ADMIN, `metrics`/`prometheus` só ADMIN |
| Só `FetchInstrumentation` | O axios usa XHR: nenhum span das chamadas à API | XHR + fetch |
| `new Resource`, `addSpanProcessor`, `SimpleSpanProcessor` | API removida no SDK 2.x; um envio por span | `resourceFromAttributes`, `spanProcessors`, `BatchSpanProcessor` |
| Exporter em `http://localhost:4318` fixo | Só funciona na máquina do collector; exige CORS | Proxy `/otlp` na mesma origem; opt-in `VITE_OTEL_ENABLED` |
| `propagateTraceHeaderCorsUrls: [/localhost:8080/]` | Falha pelo IP da rede; casa com URLs de terceiros | Origem da API, escapada e ancorada |
| `import './instrumentation'` estático, sempre ligado | SDK sempre no bundle | Import dinâmico, só quando ligado, antes do render |
| CORS da API sem `traceparent` (não previsto) | Preflight recusado: o front inteiro para | `traceparent`/`tracestate` em `ALLOWED_HEADERS` |
| `src/instrumentation.js` (JS) | O projeto é TypeScript com testes | `src/observability/*.ts` com testes |
