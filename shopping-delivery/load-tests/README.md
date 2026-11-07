# Testes da API com k6 (funcional, carga, estresse e alertas)

Testes que rodam contra a **API de verdade** (`http://localhost:8080` por padrão), via Docker: não precisa
instalar o [k6](https://grafana.com/docs/k6/). Versão fixada em `run.sh` (`K6_IMAGE`).

| Comando (a partir de `shopping-delivery/`) | Para quê | Duração |
|---|---|---|
| `./load-tests/run.sh smoke` | **A API funciona?** Percorre o fluxo de compra e confere 23 respostas | segundos |
| `./load-tests/run.sh load` | **Aguenta a carga esperada?** Taxa constante de ações/s, com latência por rota | 3 min |
| `./load-tests/run.sh stress` | **Qual é o limite?** Sobe a taxa em degraus até a API sair do SLO | até ~5 min |
| `./load-tests/run.sh alerts` | **Os alertas funcionam?** Dispara de propósito os alertas do Grafana (e o Telegram) | ~3 min |
| `./load-tests/prod-like/prod-like.sh up` + `stress` / `smoke` / `edge` | **E no ambiente de produção?** A mesma bateria contra a pilha de `deploy/` (PostgreSQL + nginx + TLS) | ver abaixo |

O código de saída é 0 quando os critérios (*thresholds*) passam e diferente de 0 quando não passam. O `smoke` roda
no CI a cada push, contra a API subida a partir do jar.

## O que cada teste faz

### `smoke.js`: funcional
Com 1 usuário novo, o teste passa por:
- **saúde e catálogo:** health check, métricas protegidas (401), lista, busca, detalhe, produto inexistente (404);
- **acesso:** cadastro, login certo e errado (400), carrinho sem login (401), admin para cliente (403);
- **compra:** adicionar e alterar o carrinho, endereço, checkout Pix (201, `PENDING`, copia e cola `000201…`),
  carrinho esvaziado, checkout com carrinho vazio (400);
- **depois da compra:** "meus pedidos", cancelamento (devolve o estoque), logout e token revogado (401).

Qualquer verificação que falhe reprova o teste. Obs.: `itemCount` do carrinho conta **linhas** (produtos
distintos), não unidades; é o "N itens" do front.

### `load.js`: carga constante
- `RATE` ações/s (padrão 20) por `DURATION` (padrão `3m`), com chegada independente da velocidade da API, como
  clientes reais.
- **Mistura de uma loja:**

  | Ação | Peso |
  |---|---|
  | Lista de produtos | 45% |
  | Busca | 10% |
  | Detalhe de produto | 25% |
  | Ver carrinho | 12% |
  | Alterar a quantidade no carrinho | 8% |

  Cada ação é uma requisição, então ações/s ≈ requisições/s.
- Usa 10 clientes de teste (`USERS`), cadastrados no início, cada um com 1 item no carrinho. As escritas só
  alternam a quantidade, para não esgotar o estoque.
- **Critérios:** erro < 1%, p95 < `SLO_MS` (padrão 500 ms) no geral e por rota, e o k6 consegue gerar a taxa.

### `stress.js`: limite em degraus
- Mesma mistura do `load.js`, em patamares de `STEP_SECONDS` (padrão 45 s): `STEPS=25,50,100,200,400,800,1600`.
- No fim, imprime uma tabela por degrau com: alvo, obtido, p50, p95, p99, % de erro, % de descartadas e resultado.
- **O limite** é o último degrau que cumpre o SLO: erro < 1%, p95 < `SLO_MS`, descartadas < 1% e obtido ≥ 99% do
  alvo.
- O teste **para sozinho** num degrau com mais de 20% de erro, para não derrubar a máquina.
- O resultado também vai para `load-tests/results/stress-<data>.json`, ignorado pelo Git.
- **"descart. %"** são as ações que o k6 não conseguiu iniciar por falta de VUs livres: a API está lenta demais
  para a taxa pedida, ou a máquina não dá conta de gerar a carga.

### `alerts.js`: disparar alertas
São três simulações, escolhidas com `DRILL=forca-bruta|varredura|webhook|todos`:

| Simulação | O que faz | Alerta esperado |
|---|---|---|
| `forca-bruta` | 1 login errado/s para emails inexistentes, por 3 min | "Muitos logins recusados" e "Rate limit acionado" (após ~2 a 3 min) |
| `varredura` | 1 acesso sem token/s a rotas protegidas, por 3 min | "Pico de 401/403" (após ~2 a 3 min) |
| `webhook` | 1 webhook Pix sem assinatura | "Webhook Pix recusado" (na hora) |

Nenhuma conta real é usada. Durante a `forca-bruta`, o rate limit de login (20/min por IP) também vale para o seu
navegador nesta máquina por até 1 minuto.

## Variáveis

| Variável | Padrão | Teste |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | todos |
| `RATE`, `DURATION`, `USERS` | `20`, `3m`, `10` | load |
| `STEPS`, `STEP_SECONDS`, `MAX_VUS` | `25,…,1600`, `45`, `2000` | stress |
| `SLO_MS` | `500` | load, stress |
| `DRILL` | `todos` | alerts |
| `MIX` | `leitura` (`completa` no prod-like) | load, stress |
| `BUYERS` | `100` | load, stress com `MIX=completa` |
| `HEALTH_URL`, `PROD_LIKE`, `K6_INSECURE_SKIP_TLS_VERIFY`, `MAX_REGISTER` | definidos pelo `prod-like.sh` | todos |

Exemplo: `RATE=100 DURATION=5m SLO_MS=300 ./load-tests/run.sh load`. Argumentos extras vão para o k6
(ex.: `--quiet`).

## Acompanhar no Grafana

Durante `load` e `stress`, abra o dashboard **ShopEasy API — Visão geral** (http://localhost:3000), com o intervalo
de tempo "Last 15 minutes". Ele mostra:
- requisições/s e latência p95 por rota;
- respostas por status;
- CPU, heap, GC e threads da JVM;
- pool de conexões e fila de espera.

O k6 mostra o que o **cliente** viu; o Grafana mostra o que aconteceu **dentro** da API. Cruzar os dois indica o
gargalo: CPU, GC, pool do banco ou uma rota específica. Os traces lentos aparecem na tabela "Requisições lentas".

## Cuidados

- **O resultado vale para esta máquina e este ambiente**, não para produção:
  - em dev o banco é o H2 em memória, e não o PostgreSQL;
  - o k6 roda na mesma máquina e disputa CPU com a API;
  - o agente OpenTelemetry acrescenta um pouco de custo.

  Use os números para comparar versões e achar gargalos. Para dimensionar a produção, rode `stress` contra um
  ambiente igual ao de produção, a partir de outra máquina.
- **Rate limit:** cadastro e login aceitam 20 requisições/min por IP. Cada execução de `load`/`stress` cadastra
  `USERS` clientes. Se rodar dois testes em menos de 1 minuto, o segundo pode falhar no cadastro (a mensagem diz
  isso). Espere 1 minuto.
- **Não rode `stress` contra a produção** sem combinar: o objetivo dele é levar a API ao limite.
- Os dados de teste ficam no banco de dev (usuários `*@carga.teste`, carrinhos, um pedido cancelado do `smoke`).
  Com o H2 em memória, eles somem quando a API reinicia.

## Ambiente de produção (`prod-like/`)

`prod-like/prod-like.sh` sobe **a pilha de produção de `deploy/`**, sem alterá-la, com o projeto Docker
`shopeasy-loadtest`. Ela tem:
- PostgreSQL 16;
- a API no perfil `prod`, num container somente leitura e sem *capabilities*, com o validador de configuração ativo;
- nginx com TLS e os cabeçalhos de segurança.

O script gera em `prod-like/generated/` (ignorado pelo Git) segredos descartáveis, um certificado autoassinado e a
configuração do nginx.

```bash
./load-tests/prod-like/prod-like.sh up       # 1ª vez ~10 min (constrói as imagens); depois ~1 min
./load-tests/prod-like/prod-like.sh smoke    # 24 verificações através do nginx (inclui: /actuator não exposto, HSTS/CSP)
./load-tests/prod-like/prod-like.sh stress   # MIX=completa + CPU/memória de cada container (CSV em results/)
NGINX_LIMITS=prod ./load-tests/prod-like/prod-like.sh up && RATE=30 ./load-tests/prod-like/prod-like.sh edge
./load-tests/prod-like/prod-like.sh down     # apaga os dados do teste
```

**O que muda em relação à produção** (em `docker-compose.loadtest.yml`, só no teste). O limite de memória da API
(`API_MEM_LIMIT`, padrão 1536m) já vem do próprio `deploy/docker-compose.prod.yml`.
- **Rate limits da API e do nginx bem maiores:** o k6 sai de um IP só, e com os limites reais o teste mediria o
  limitador, não a API. Os limitadores continuam ligados. `NGINX_LIMITS=prod` usa os limites reais.
- **Porta só em `127.0.0.1:8443`.**

**`MIX=completa`:** além da navegação, inclui login (3%, BCrypt) e compra (5%: carrinho → checkout Pix →
cancelamento, que devolve o estoque).

## Resultados de referência

Medido em 2026-11-07 num notebook AMD Ryzen 5 3500U (8 threads, 9 GB de RAM), Java 17. Perfil dev (H2 em memória),
API com o agente OpenTelemetry (`scripts/run-backend.sh`), k6 na mesma máquina, SLO de p95 < 500 ms.

| alvo/s | obtido/s | p50 | p95 | p99 | erro | resultado |
|---|---|---|---|---|---|---|
| 25 a 800 | = alvo | 2–4 ms | 4–9 ms | 6–11 ms | 0% | OK |
| 1.600 | 1.600 | 2 ms | 9–48 ms | 28–456 ms | 0% | OK (o p99 começa a subir) |
| 2.400 | 2.400 | 133 ms | 511 ms | 913 ms | 0% | acima do SLO |
| 3.200 a 6.400 | **~2.800–2.900** | ~1.040 ms | ~1.400 ms | ~1.700 ms | ~0% | saturada |

**Conclusões:**
- **Capacidade dentro do SLO: ~1.600 ações/s.** A latência é estável até aí.
- **Vazão máxima: ~2.900 requisições/s.** Acima disso, a API não responde mais rápido, só enfileira, e a latência
  passa de 1 s.
- **Degradação suave:** mesmo com o dobro da vazão máxima, quase não houve erro (0,01%) e a API continuou de pé.
  O que piora é o tempo de resposta.
- **Gargalo: CPU.** A carga da máquina passou de 60, com o k6 disputando a CPU com a API. Numa máquina dedicada à
  API, o limite tende a ser maior.

Para a sua máquina, rode `./load-tests/run.sh stress` e, se todos os degraus passarem,
`STEPS=1600,2400,3200,4800 STEP_SECONDS=30 ./load-tests/run.sh stress`.

### Ambiente de produção (2026-11-07, mesma máquina, `prod-like.sh`)

| Mistura | Dentro do SLO | Vazão máxima | Erros acima do limite |
|---|---|---|---|
| Leitura (a mesma do dev) | 400/s OK (p95 6,5 ms); 800/s já passa (p95 522 ms) | **~1.190 req/s** | 0% |
| **Completa** (login e compra) | **~400 ações/s** (~440 req/s, p95 41–104 ms, 99,8–100% das compras OK) | ~600–675 ações/s (~650–730 req/s) | ~2%; ~45% das compras |

**CPU no pico** (100% = 1 núcleo, a máquina tem 8 threads):

| Container | Pico | Leitura |
|---|---|---|
| API | ~480% | o maior consumidor |
| **nginx (TLS)** | **~450%** | o TLS custa quase tanto quanto a API |
| PostgreSQL | ~65–86% | folgado; nem o banco nem o pool de 10 conexões foram gargalo |

A memória ficou folgada: a API chegou a 555 MiB de 1.536.

**Conclusões:**
- **Capacidade real de referência: ~400 ações/s**, 4 vezes menos que no dev. O ambiente (PostgreSQL e nginx com
  TLS) derruba cerca de 2,4 vezes, e a mistura com login e compra, cerca de 2 vezes.
- Para uma loja pequena ou média, isso ainda é **folgado**: 400 ações/s correspondem a algo como 2 a 4 mil pessoas
  navegando ao mesmo tempo.
- **Gargalo: CPU**, dividida entre a API, o TLS do nginx e o próprio k6. Num servidor dedicado, sem o k6, o número
  tende a ser maior. Em produção, considere terminar o TLS num balanceador ou CDN.
- **Integridade:** depois de 3.484 pedidos criados e cancelados sob saturação, o estoque voltou exatamente ao
  inicial. Não houve venda acima do estoque.
- **As falhas de compra acima do limite** vêm de duas causas: o teste (2.000 VUs dividindo 100 compradores, e um
  esvazia o carrinho do outro) e o estoque segurado por pedidos pendentes nos produtos com 10 a 15 unidades. Dentro
  do limite, 100% das compras deram certo.

**Problemas encontrados por este teste, já corrigidos** e confirmados repetindo o teste: depois das correções,
**0 respostas 5xx** e nenhum `duplicate key` ou `deadlock` no log.

| Problema | Evidência antes | Correção |
|---|---|---|
| **Corrida ao adicionar o mesmo produto ao carrinho** | Duas requisições simultâneas do mesmo usuário e produto: as duas inseriam, e a segunda quebrava `cart_items_user_id_product_id_key` → **500** (duplo clique, duas abas) | `CartService.addItem` trava a linha do usuário (`SELECT … FOR UPDATE`) antes de "procura, senão insere" |
| **Deadlock entre checkout e cancelamento** (só no PostgreSQL) | O checkout de [A, B] e o cancelamento de [B, A] travavam os produtos em ordens opostas → `deadlock detected` → **500** (4 vezes numa rodada) | Baixa e devolução do estoque sempre em ordem de id do produto (`OrderService.checkout`, `StockService.restore`) |
| Violação de restrição ou vítima de trava viravam 500 | Por exemplo, dois cadastros simultâneos com o mesmo email | `GlobalExceptionHandler` → **409** sem detalhes do SQL (o cliente pode repetir) |
| **Rate limit da borda por IP** (`20r/s`, `burst=40`) | Um único IP: 30 req/s → **29%** de 429; 60 → **66%**. Clientes atrás do mesmo IP (CGNAT, empresa, Wi-Fi público) somam requisições | `100r/s`, `burst=200` (o login continua com `10r/m`). Depois: 30 e 60 req/s → **0%**; 120 → 11%; 300 → 64% (ainda corta abuso) |
| **API sem limite de memória** em `deploy/docker-compose.prod.yml` | A JVM dimensionava o heap pela RAM da máquina inteira | `mem_limit: ${API_MEM_LIMIT:-1536m}` (pico medido: 558 MiB) |
| Cabeçalhos de segurança **duplicados** (API e nginx) | Cinco cabeçalhos repetidos; `Referrer-Policy` com dois valores diferentes | `proxy_hide_header` em `deploy/proxy-api.inc`: a borda é a fonte única. O `smoke` (prod-like) confere |

Testes que reproduzem as corridas e falhavam antes da correção: `ConcurrencyIntegrationTest`, `StockServiceTest` e o
teste da ordem da baixa em `OrderServiceTest`.
