# Testes da API com k6 (funcional, carga, estresse e alertas)

Testes que rodam contra a **API de verdade** (`http://localhost:8080` por padrão), via Docker: não precisa
instalar o [k6](https://grafana.com/docs/k6/). Versão fixada em `run.sh` (`K6_IMAGE`).

| Comando (a partir de `shopping-delivery/`) | Para quê | Duração |
|---|---|---|
| `./load-tests/run.sh smoke` | **A API funciona?** Percorre o fluxo de compra e confere 23 respostas | segundos |
| `./load-tests/run.sh load` | **Aguenta a carga esperada?** Taxa constante de ações/s, com latência por rota | 3 min |
| `./load-tests/run.sh stress` | **Qual é o limite?** Sobe a taxa em degraus até a API sair do SLO | até ~5 min |
| `./load-tests/run.sh alerts` | **Os alertas funcionam?** Dispara de propósito os alertas do Grafana (e o Telegram) | ~3 min |

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
