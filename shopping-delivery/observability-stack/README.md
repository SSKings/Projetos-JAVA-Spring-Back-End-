# Observabilidade (OpenTelemetry + Grafana LGTM)

Stack local para ver **traces, métricas e logs** do backend e do frontend, correlacionados pelo `trace_id`.
Decisão e motivos: [ADR-022](../docs/adr.md#adr-022--observabilidade-com-opentelemetry-e-grafana-lgtm).

```
navegador (frontend-react) ──/otlp (proxy do Vite)──┐
                     │ traceparent                  ▼
API (backend-spring + agente Java) ──OTLP 4318──► OTel Collector ──► Tempo      (traces)
                                                       │──────────► Loki       (logs, OTLP nativo)
                                                       └─:8889────► Prometheus (métricas, scrape)
                                                                     ▲
                                                 Grafana :3000 ──────┘ (datasources já correlacionados)
```

## Como usar

```bash
# 1. Stack (Docker) — a partir de shopping-delivery/
cd observability-stack && docker compose up -d && cd ..

# 2. API com o agente OpenTelemetry (baixa o agente fixado no pom.xml, com checksum, e gera o jar)
./scripts/run-backend.sh                  # SKIP_BUILD=1 reaproveita o jar; argumentos vão para a aplicação

# 3. Front com a instrumentação ligada — a partir da raiz do repositório
cd ../shopping-delivery-frontend
VITE_OTEL_ENABLED=true npm run dev        # ou coloque VITE_OTEL_ENABLED=true no .env.local

# 4. (opcional) Alertas no Telegram — nesta pasta; depois disso, o `docker compose up -d` já inclui o Telegram
./telegram-setup.sh
```

Use a loja em `http://localhost:5173` e abra o **Grafana em `http://localhost:3000`** (sem login, ver abaixo).

### Dashboards (pasta **ShopEasy**, provisionados de `dashboards/`)

| Dashboard | Para quê |
|---|---|
| **ShopEasy API — Visão geral** (home) | A API está saudável? Tem requisições/s, % de erros, latência p50/p95/p99 por rota, tabela de rotas, recusas de segurança (401/403/413/429), login, JVM (heap, CPU, GC, threads), pool do banco, logs de erro e traces lentos ou com erro. Filtre pela variável **Rota** |
| **ShopEasy — Negócio e segurança** | O que está acontecendo na loja? Pedidos criados e recusados, Pix confirmados e recusados no webhook, cadastros, logins recusados ou bloqueados, cancelamentos, devoluções, estornos, ações e acessos negados no admin |

- Clicar num trace das tabelas abre a árvore da requisição; num log, o link *TraceID* faz o mesmo.
- Alterações feitas no Grafana não ficam salvas: exporte o JSON para `dashboards/` e faça commit. As regras estão
  na seção 4.6 do [guia](../docs/instrunctions-cloud-impl-observability.md).

### Alertas (pasta **ShopEasy — Alertas**, provisionados de `alerting/`)

São 12 regras avaliadas a cada minuto (*Alerting → Alert rules*):
- **disponibilidade:** API sem telemetria, 5xx > 5%, latência p95 > 1 s, fila no pool do banco;
- **segurança:** pico de 401/403, força bruta no login, rate limit, webhook Pix recusado;
- **recursos:** pool > 90%, heap após GC > 85%, GC > 10%, CPU > 90%.

Cada alerta traz o valor medido, **o que fazer** e um link para o painel. Sem configuração, eles só aparecem no
Grafana. Para recebê-los no celular, configure o Telegram (abaixo). Detalhes nas seções 4.7 e 4.8 do
[guia](../docs/instrunctions-cloud-impl-observability.md).

### Notificações no Telegram

**1. Crie o bot (no celular ou no Telegram Desktop).** Abra o **@BotFather**, envie `/newbot`, escolha um nome e um
usuário terminado em `bot`. Ele responde com o **token** (`123456789:AA...`). Trate o token como senha.

**2. Rode o assistente num terminal, nesta pasta:**
```bash
./telegram-setup.sh
```
Ele guia o resto, um passo por vez:

| Passo | O que acontece |
|---|---|
| 1. Token | Você cola o token (ele não aparece na tela) e o assistente confere com o Telegram |
| 2. Chat | Você manda `/start` ao seu bot e tecla Enter; o assistente descobre o chat ID. Para um grupo, adicione o bot ao grupo e mande uma mensagem lá |
| 3. Teste direto | O bot manda "✅ ShopEasy: bot configurado" no seu Telegram |
| 4. Configuração | Grava o `.env` (ignorado pelo Git, permissão 600) e recria o Grafana com o Telegram |
| 5. Teste pelo Grafana | O Grafana envia uma notificação de teste, a mesma do botão *Test* da interface, usando o token armazenado |

**3. Veja alertas de verdade chegando:**
```bash
../load-tests/run.sh alerts
```
O script simula força bruta, varredura e webhook forjado. "Webhook Pix recusado" chega em cerca de 1 minuto e os
outros em 3 a 4 minutos. As resoluções (✅) chegam alguns minutos depois do fim da simulação.

**Desligar:** apague a linha `COMPOSE_FILE` do `.env` e rode `docker compose up -d`.

**Se algo falhar,** o assistente diz em qual passo e por quê. Os casos mais comuns:

| Sintoma | Causa |
|---|---|
| "O Telegram recusou o token" | O token foi copiado errado ou revogado |
| "Ainda não chegou nenhuma mensagem ao bot" | Faltou o `/start` |
| O Grafana não sobe | Veja `docker logs grafana`: valores inválidos aparecem como `telegram: ... inválido` |

<details><summary>Configuração manual (sem o assistente)</summary>

1. Descubra o chat ID depois de mandar `/start` ao bot:
   ```bash
   read -rsp "Token do bot: " TOKEN; echo
   curl -s "https://api.telegram.org/bot$TOKEN/getUpdates" | python3 -c \
     "import json,sys; [print(u['message']['chat']['id'], u['message']['chat'].get('title') or u['message']['chat'].get('first_name')) for u in json.load(sys.stdin)['result'] if 'message' in u]"
   ```
   Usuário é um número positivo; grupo é negativo (`-100...`).
2. Copie o modelo com `cp .env.example .env`.
3. Descomente e preencha `COMPOSE_FILE`, `TELEGRAM_BOT_TOKEN` e `TELEGRAM_CHAT_ID`.
4. Rode `docker compose up -d`.
5. Teste em **Alerting → Contact points → Telegram ShopEasy → Test**.
</details>

### Explorar livremente

- **Explore → Tempo → Search**: os traces começam no `frontend-react` (requisição XHR do axios) e continuam no
  `backend-spring` (`GET /api/products` → repositório → SQL), tudo no mesmo trace.
- **Explore → Loki**: `{service_name="backend-spring"}`. Cada linha tem `trace_id`; o link *TraceID* abre o trace.
  Também no console da API: `INFO [trace_id=… span_id=…]`.
- **Explore → Prometheus**: por exemplo `http_server_request_duration_seconds_count{service_name="backend-spring"}`.
- Sem o agente (`./mvnw spring-boot:run`) a API funciona normalmente e só não envia telemetria.

Parar: `docker compose down` (os dados ficam dentro dos containers e somem com eles).

## O que mudou em relação a `docs/instrunctions-cloud-impl-observability.md`

A arquitetura é a da especificação. Estes pontos foram corrigidos porque, como estavam, não funcionavam ou abriam
risco. Cada um foi verificado com a stack rodando.

| Especificação | Problema | Aqui |
|---|---|---|
| Imagens `:latest` e agente de `releases/latest` sem verificação | Build não reproduzível; uma release nova (ou um download adulterado) roda na JVM com acesso a tudo | Versões fixas; o agente vem do Maven Central via `./mvnw -C dependency:copy` (checksum obrigatório), versão em `pom.xml` → `otel-javaagent.version` |
| Portas `4317:4317`, `3000:3000`… | Abertas para a rede, e o Grafana tem admin anônimo | Todas presas a `127.0.0.1` |
| Exporter `otlp/loki` sem pipeline de logs | Os logs nunca chegavam ao Loki | `otlphttp/loki` → `http://loki:3100/otlp` + pipeline `logs` |
| Tempo `block_Retention` | Chave inválida: o Tempo não sobe | `block_retention` |
| Agente apontando para `:4317` | O agente 2.x usa `http/protobuf` por padrão, que é a porta 4318 | `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318` |
| Datasources sem `uid` | Links trace → logs → trace não resolvem | uids `prometheus`, `tempo`, `loki`; derived field por label `trace_id` |
| Front só com `FetchInstrumentation` | O axios usa XHR: nenhuma chamada à API viraria span | `XMLHttpRequestInstrumentation` + `FetchInstrumentation` |
| `new Resource(...)`, `addSpanProcessor`, `SimpleSpanProcessor` | API removida no SDK 2.x; um envio HTTP por span | `resourceFromAttributes`, `spanProcessors` no construtor, `BatchSpanProcessor` |
| Exporter em `http://localhost:4318` fixo | Só funciona na máquina do collector e exige CORS | Proxy `/otlp` no Vite (mesma origem); opt-in com `VITE_OTEL_ENABLED` |
| `propagateTraceHeaderCorsUrls: /localhost:8080/` | Não funciona pelo IP da rede; a regex casa com qualquer URL que contenha o texto | Só a API desta aplicação (`resolveApiBaseUrl`), com âncora |
| CORS da API sem `traceparent` | O preflight falha e **todas** as chamadas do front param | `traceparent` e `tracestate` em `CorsConfig.ALLOWED_HEADERS` |
| Actuator com `show-details=always` | Expõe detalhes de banco e disco a qualquer um | `health` público só com UP/DOWN; detalhes, `metrics` e `prometheus` só ADMIN |

## Segurança e privacidade

- **Uso local.** As portas ficam em `127.0.0.1` e o Grafana dá Admin a quem abrir. Para outro ambiente: tire o
  acesso anônimo, troque a senha (`GRAFANA_ADMIN_PASSWORD`) e coloque TLS na frente.
- **Os logs vão para o Loki.** Em dev isso inclui a senha do admin gerada no start (ADR-021). Não use esta stack
  com dados reais de clientes sem rever o que é logado (LGPD). Os spans HTTP levam URL, método e status, não o
  corpo nem o cabeçalho `Authorization`.
- **Proxy `/otlp`.** Como o Vite escuta na rede (ADR-015), outras máquinas da rede também podem enviar spans para
  o collector por ele. Em dev é aceitável; é só telemetria.
- **Produção.** A stack não faz parte do `deploy/`. O nginx só repassa `/api/`, então `/actuator` não é exposto e
  a CSP (`connect-src 'self'`) bloqueia exporter para outra origem. Para observar a produção, use um collector
  atrás do nginx e um backend de telemetria com autenticação.
