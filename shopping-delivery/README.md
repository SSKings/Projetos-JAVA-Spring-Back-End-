# ShopEasy — E-commerce Simples

Projeto full-stack de e-commerce com API REST em Spring Boot e interface em React + TypeScript,
com loja para clientes e **área administrativa** para gerenciar produtos, pedidos e usuários.

## Estrutura do repositório

```
Projetos-JAVA-Spring-Back-End-/
├── shopping-delivery/
│   ├── shopping-delivery/        ← Backend (Spring Boot)
│   │   ├── src/
│   │   ├── Dockerfile            ← imagem da API (sem root)
│   │   └── pom.xml
│   ├── deploy/                   ← produção: docker-compose, nginx (TLS/CSP), .env.prod.example
│   ├── observability-stack/      ← OpenTelemetry Collector + Grafana/Tempo/Loki/Prometheus, dashboards, alertas e Telegram (dev, ADR-022)
│   ├── load-tests/               ← testes k6 da API: smoke (CI), carga, estresse, alertas
│   ├── scripts/                  ← simulate-pix-payment.sh (+ teste), run-backend.sh (API com agente OTel),
│   │                               check-dependency-cves.py (+ teste), test_grafana_provisioning.py
│   └── docs/
│       ├── api-reference.md      ← Referência completa dos endpoints
│       ├── architecture.md       ← Modelo de domínio e decisões técnicas
│       ├── adr.md                ← Architecture Decision Records
│       ├── dev-workflow.md       ← Branches, worktrees e servidores locais
│       ├── instrunctions-cloud-impl-observability.md ← Guia de observabilidade (especificação e manutenção)
│       └── security-review.md    ← Revisão de segurança, status das correções, checklist legal
└── shopping-delivery-frontend/   ← Frontend (React + TS), na raiz do repositório
    ├── src/
    └── package.json
```

## Stack

| Camada       | Tecnologia                                          |
|--------------|-----------------------------------------------------|
| API          | Spring Boot 3.5.16, Java 17, Spring Security + JWT, MFA TOTP |
| Persistência | Spring Data JPA, H2 (dev), PostgreSQL (prod), Flyway |
| Frontend     | React 19, TypeScript, Tailwind CSS, React Router v7 |
| HTTP client  | Axios (com interceptors de auth e 401 redirect)     |
| Testes BE    | JUnit 5, Mockito, `@WebMvcTest`, JaCoCo             |
| Testes FE    | Vitest, Testing Library, `@vitest/coverage-v8`      |
| Observabilidade | OpenTelemetry (agente Java 2.33 + SDK web), Collector, Grafana, Tempo, Loki, Prometheus; alertas no Telegram |
| Testes da API | k6 1.3 (via Docker): funcional, carga, estresse e simulação de alertas |

## Pré-requisitos

- Java 17+
- Maven: não é preciso instalar, o projeto usa o wrapper `./mvnw` (Maven 3.10.0)
- Node.js 22.22+ (recomendado 24 — exigido pelo Vitest/jsdom)
- Docker: só para a pilha de produção (`deploy/`) e para a observabilidade (`observability-stack/`)

## Rodando localmente

```bash
# 1. Backend (porta 8080)
cd shopping-delivery/shopping-delivery
./mvnw spring-boot:run

# 2. Frontend (porta 5173) — a partir da raiz do repositório
cd shopping-delivery-frontend
npm ci          # instala exatamente o package-lock.json (rode de novo após cada git pull)
npm run dev     # antes de subir, avisa se a branch não tem commits da main
```

Acesse `http://localhost:5173` — a API estará em `http://localhost:8080`.

**E-mails em dev (ADR-025):** suba o Mailpit e veja os e-mails (confirmação de cadastro, nova senha, avisos de pedido)
em http://localhost:8025 — nada sai para a internet. Sem ele a API funciona, mas os e-mails só aparecem no log.

```bash
cd shopping-delivery/mailpit && docker compose up -d
```

Para fechar pedido, a conta precisa ter o e-mail confirmado: abra o link do e-mail no Mailpit. Para pular isso em
dev, rode a API com `REQUIRE_VERIFIED_EMAIL=false`.

## Observabilidade (traces, métricas e logs)

Opcional, para desenvolvimento. Mostra cada clique do front até o SQL no Grafana:

```bash
cd shopping-delivery/observability-stack && docker compose up -d   # Grafana em http://localhost:3000
cd .. && ./scripts/run-backend.sh                                 # API com o agente OpenTelemetry
cd ../shopping-delivery-frontend && VITE_OTEL_ENABLED=true npm run dev
```

Dashboards prontos na pasta **ShopEasy** do Grafana: "ShopEasy API — Visão geral" (home) e "ShopEasy — Negócio e segurança"; 12 alertas na pasta **ShopEasy — Alertas**.
Para receber os alertas no Telegram: `cd observability-stack && ./telegram-setup.sh` (assistente passo a passo).
Detalhes, correções em relação à especificação e cuidados de segurança: [observability-stack/README.md](observability-stack/README.md).

> Depois de trocar de branch, **reinicie a API** (o Vite recarrega sozinho; o Spring não). Trabalho paralelo
> vai num `git worktree`, não no checkout em uso — ver [`docs/dev-workflow.md`](docs/dev-workflow.md).

O perfil padrão é **`dev`**:
- H2 em memória;
- Swagger e console H2 ligados;
- segredo JWT e senha do admin **aleatórios a cada start**;
- segredo do webhook Pix de exemplo, para o script de simulação.

**Login de administrador (dev):** `admin@shopeasy.com` e a **senha mostrada no log da API** ao subir
(bloco "Senha do admin de desenvolvimento"). Ela muda a cada start; para fixar, defina `ADMIN_PASSWORD`
(ex.: `ADMIN_PASSWORD=MinhaSenha123 ./mvnw spring-boot:run`). Depois, link **Admin** na barra superior.
Em produção o admin vem de `ADMIN_EMAIL`/`ADMIN_PASSWORD` e precisa ativar a verificação em duas etapas
(**Minha conta → Segurança**) antes de usar a área administrativa.

> Console H2 disponível em `http://localhost:8080/h2-console`
> Swagger UI em `http://localhost:8080/swagger-ui/index.html`

## Acessando de outros computadores na rede

O Vite escuta em todas as interfaces (`server.host: true` em `vite.config.ts`) e o front-end chama a API
**no mesmo host pelo qual a página foi aberta** (`src/api/baseUrl.ts`). Então basta:

1. Rodar backend e frontend nesta máquina.
2. Descobrir o IP dela na rede: `hostname -I` (Linux) — ex.: `192.168.1.180`. O `npm run dev` também mostra
   a linha `Network: http://192.168.1.180:5173/`.
3. No outro computador, abrir `http://192.168.1.180:5173`. A página chamará `http://192.168.1.180:8080`.
4. Se não abrir, libere as portas no firewall desta máquina:
   ```bash
   sudo ufw allow 5173/tcp   # front-end (Vite)
   sudo ufw allow 8080/tcp   # API
   ```

> ⚠️ Em rede compartilhada, o ambiente de dev fica visível para as outras máquinas. O segredo JWT e a senha
> do admin são aleatórios a cada start (revisão de segurança 2), e o console H2 e o Swagger só respondem na própria
> máquina (revisão 3). O webhook de dev (segredo público, para o script de simulação) continua aberto.
> Não use o perfil `dev` em rede pública.

## Pagamento via Pix

O checkout gera um **Pix "copia e cola" e QR Code** no padrão BR Code do Banco Central, com o valor do pedido,
a chave da loja e um identificador (`txid`) único. O cliente paga no app do banco e a tela atualiza sozinha.

| Variável                 | Padrão (dev)                         | O que é |
|--------------------------|--------------------------------------|---------|
| `PIX_KEY`                | `pix@shopeasy.example`               | Chave Pix da loja (email, CPF/CNPJ só dígitos, `+55…` ou aleatória). O padrão usa o domínio reservado `.example`: não existe no DICT, então nenhum teste paga para terceiros. **Defina a chave real para receber.** |
| `PIX_MERCHANT_NAME`      | `ShopEasy`                           | Nome do recebedor no app do banco (até 25 caracteres) |
| `PIX_MERCHANT_CITY`      | `Sao Paulo`                          | Cidade do recebedor (até 15) |
| `PIX_EXPIRATION_MINUTES` | `30`                                 | Validade do QR Code; vencido = pedido cancelado e estoque devolvido |
| `PIX_WEBHOOK_SECRET`     | `dev-pix-webhook-secret-change-me`   | Segredo HMAC do webhook do PSP. **Troque em produção**; vazio desativa o webhook |

**Como o pagamento é confirmado.** Um QR Code com chave Pix não avisa a loja sozinho. Há dois caminhos:

1. **Webhook de um PSP** (banco/gateway com API Pix): ele chama `POST /api/payments/pix/webhook` assinado com
   HMAC-SHA256 (`X-Pix-Timestamp` + `X-Pix-Signature`). O back-end confere a assinatura, a janela de 5 minutos e
   o valor exato antes de marcar como pago. Contrato em [`docs/api-reference.md`](docs/api-reference.md#pagamentos-pix).
2. **Manual**: o admin vê o Pix no extrato (mesmo valor e identificador) e clica em **Confirmar Pix**.

Para **simular** o PSP em desenvolvimento:

```bash
# Cole o "Pix copia e cola" da tela de pagamento ENTRE ASPAS (ele tem espaços); txid e valor são lidos do código
./scripts/simulate-pix-payment.sh '00020101021226...6304F71A'

./scripts/simulate-pix-payment.sh '<copia e cola>' 1.00   # força outro valor (a API recusa: valor divergente)
./scripts/simulate-pix-payment.sh <txid> 49.90             # ou txid ("Identificador" na tela) + valor
./scripts/simulate-pix-payment.sh --dry-run '<copia e cola>'  # só mostra o que seria enviado
```

O script confere o CRC do código (detecta cópia incompleta) e valida txid e valor antes de enviar. Testes:
`./scripts/simulate-pix-payment.test.sh` (também rodam no CI).

> Para receber de verdade, defina `PIX_KEY` com a chave real da loja. Sem PSP, use a confirmação manual.

## Bot de administração no Telegram

Um bot (diferente do bot dos alertas do Grafana) que **avisa sobre os pedidos** e deixa o admin **gerenciar pedidos e
usuários pelo celular** (ADR-024). Ele roda dentro da API, só com conexões de saída para o Telegram, e vem desligado.

**Configurar (uma vez):**

1. No Telegram, abra o **@BotFather**, envie `/newbot` e guarde o token.
2. Rode o assistente e siga as perguntas: token, e-mail da conta admin e `/start` no bot.
   ```bash
   ./scripts/telegram-admin-bot-setup.sh
   ```
3. Reinicie a API (`./scripts/run-backend.sh`). O log mostra `Telegram: bot de administração iniciado`.
4. No Telegram, mande `/entrar 123456` com o código do app autenticador da sua conta admin (com MFA ativo). A sessão
   vale 12 horas; `/sair` encerra antes.

Para outro admin usar, rode o assistente de novo com o mesmo token: ele **adiciona** o chat à lista.

**O que chega sozinho:** novo pedido, Pix confirmado pelo PSP, cancelamento pelo cliente (com aviso de estorno se o
Pix já tinha sido pago), pedido de devolução e Pix expirado. Cada aviso traz o pedido e os botões de ação.

| Comando | O que faz |
|---|---|
| `/pedidos` · `/pedidos pendentes` | Últimos 10 pedidos, ou só os de um status (pendentes, confirmados, enviados, entregues, cancelados) |
| `/pedido 12` (ou toque em `/pedido_12`) | Detalhes do pedido, com botões: mudar status, confirmar Pix, registrar estorno |
| `/pix 12 123456` · `/estorno 12 123456` | Confirma o Pix / registra o estorno do pedido #12 — pede um **código novo** do app autenticador (dinheiro: a sessão não basta) |
| `/usuarios` · `/usuarios ana` | Últimos cadastros, ou busca por nome/e-mail |
| `/usuario 5` | Detalhes, com botões: bloquear/desbloquear cliente e ver pedidos (trocar papel e mexer em admin: só no painel, com senha e código) |
| `/resumo` | Pedidos, faturamento, clientes e produtos |

**Segurança:** só os chats da lista `TELEGRAM_ADMIN_BOT_ADMINS` (`chatId:email`), em conversa privada. A cada
mensagem, a conta precisa continuar ADMIN e ativa (em produção, também com MFA). Admin com MFA abre a sessão com o
código do app (`/entrar`); ela cai com logout, troca de senha ou bloqueio no painel. O bot também avisa os admins de
**alertas de segurança**: ataque de senha em andamento, alguém promovido a admin, admin bloqueado. Toda ação pede confirmação, segue as
mesmas regras do painel e fica na **auditoria** com o nome do admin. Para cortar o acesso de alguém, bloqueie a conta
no painel ou tire o chat da lista. Quem usa o bot deve ativar a verificação em duas etapas no Telegram.

| Variável | Padrão | O que é |
|---|---|---|
| `TELEGRAM_ADMIN_BOT_ENABLED` | `false` | Liga o bot |
| `TELEGRAM_ADMIN_BOT_TOKEN` | — | Token do @BotFather |
| `TELEGRAM_ADMIN_BOT_ADMINS` | — | `chatId:email-do-admin`, separados por vírgula |

Em dev, o assistente grava essas variáveis em `shopping-delivery/.env.telegram-admin-bot` (ignorado pelo Git,
permissão 600), e o `run-backend.sh` as carrega. Em produção, elas vão no `deploy/.env.prod`.

## Produção

```bash
cd shopping-delivery/deploy
cp .env.prod.example .env.prod          # preencha (openssl rand ... para os segredos)
mkdir certs && cp /caminho/fullchain.pem /caminho/privkey.pem certs/
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

Sobe PostgreSQL (sem porta exposta, numa rede interna sem saída para a internet, com **três usuários**: o
superusuário só cria o banco, o dono das tabelas só roda as migrações e a API só lê e grava dados —
`deploy/db-init/01-roles.sh`; banco já existente: ver `docs/security-review.md`, seção 11), API em perfil `prod` (container
read-only, sem root) e nginx (também read-only, sem capabilities) com TLS, HSTS, CSP, rate limit (login 10/min e API
100 req/s por IP) e proxy de `/api` na mesma origem. Imagens fixadas por digest. A API tem limite de
memória (`API_MEM_LIMIT`, padrão 1536m) e pool de conexões configurável (`DATABASE_POOL_SIZE`). A API **não inicia** com segredos ausentes, fracos ou
de desenvolvimento, CORS aberto ou banco H2 (`ProductionConfigValidator`). Detalhes: ADR-017 a ADR-019 e
[`docs/security-review.md`](docs/security-review.md) (status de cada correção e o que ainda depende da empresa:
CNPJ e textos legais revisados, PSP para Pix dinâmico, NF-e).

**E-mail** (confirmação de cadastro, nova senha, avisos de pedido e de segurança — ADR-025): com uma conta Gmail da
loja, ative a verificação em duas etapas, crie uma **senha de app** em https://myaccount.google.com/apppasswords e
preencha `MAIL_USERNAME`, `MAIL_PASSWORD` (a senha de app, sem espaços), `MAIL_FROM` e `APP_PUBLIC_URL` no `.env.prod`.
O Gmail aceita cerca de 500 envios por dia; para mais volume ou e-mail no domínio da loja, troque só os `MAIL_*`
(qualquer SMTP serve). A API não sobe sem STARTTLS.

**Backup** (obrigatório antes de abrir a loja): preencha `BACKUP_PASSPHRASE` no `.env.prod`, guarde-a fora do
servidor e agende o backup cifrado no cron:

```bash
0 3 * * *  /caminho/para/deploy/backup-db.sh >> /var/log/shopeasy-backup.log 2>&1
```

Copie `BACKUP_DIR` para fora do servidor e teste a restauração (`deploy/restore-db.sh <arquivo>`) em homologação
uma vez por mês.

## Fluxo principal

```
Usuário acessa produtos (público)
  → Cria conta (validada no front e no back, aceite dos termos) / faz login (MFA opcional) → recebe JWT
  → Adiciona produtos ao carrinho
  → Checkout: escolhe endereço → revisa (forma de pagamento: Pix) → paga → confirmação
  → Pedido criado (PENDING), estoque reservado, carrinho limpo, Pix gerado (QR Code + copia e cola)
  → Pix pago (webhook do PSP ou admin) → pedido CONFIRMED · Pix vencido → pedido CANCELLED, estoque devolvido
  → Minha conta (/orders): pedidos com filtro/busca e linha do tempo do status,
    histórico de compras por produto (/purchases) e endereços de entrega (/addresses)

  → Pode cancelar o pedido até o envio e pedir devolução até 7 dias após a entrega
  → Dados e privacidade: corrige, baixa (JSON) e exclui (anonimiza) os próprios dados

Administrador (/admin, MFA obrigatório em produção)
  → Dashboard: faturamento, pedidos pendentes, clientes, estoque baixo
  → Produtos: cria, edita, desativa/reativa
  → Pedidos: filtra por status e avança PENDING → CONFIRMED → SHIPPED → DELIVERED,
             podendo pular etapas (ou cancela antes do envio — o estoque é devolvido);
             pedido com Pix pendente só pode ser cancelado; "Confirmar Pix" após conferir o extrato
  → Usuários: busca, bloqueia/desbloqueia, promove a admin, vê pedidos do cliente
  → Auditoria: quem fez o quê, quando e de qual IP
```

## Testes e cobertura

| Projeto   | Comando                 | Testes | Linhas | Branches |
|-----------|-------------------------|--------|--------|----------|
| Backend   | `./mvnw verify`         | 443    | 98,4%  | 94,4%    |
| Frontend  | `npm run coverage`      | 337    | 99,1%  | 95,9%    |
| Scripts (Python) | `python3 -m unittest discover -s scripts -p 'test_*.py'` | 49 | — | — |
| Script Pix | `./scripts/simulate-pix-payment.test.sh` | 10 | — | — |
| API rodando (k6) | `./load-tests/run.sh smoke` | 23 verificações | — | — |

Os 49 testes Python cobrem o verificador de CVEs (4), a validação de dashboards, alertas e Telegram (25), o
assistente do bot de administração (6, contra um Telegram falso), o backup cifrado do banco (5) e o verificador de
imagens/actions fixadas por hash (5, inclusive a leitura da imagem do k6 pelo CI) e o executor local dos jobs do CI
(4, `scripts/run-ci-job.py`).

Os dois comandos **falham se a cobertura cair** abaixo do mínimo (backend 85% linhas / 80% branches;
frontend 90% linhas / 85% branches) e rodam no CI a cada push. No front, o CI também roda `tsc -b`, `lint` e
**`npm run build`**: os testes não processam CSS, então só o build pega quebras de Tailwind/PostCSS (ADR-020).
O CI também verifica vulnerabilidades conhecidas nas dependências: `scripts/check-dependency-cves.py` (backend, banco
OSV, inclui o que vem do BOM do Spring Boot) e `npm audit --omit=dev --audit-level=high` (front) — ADR-021.
Relatórios HTML: `shopping-delivery/target/site/jacoco/index.html` e `shopping-delivery-frontend/coverage/index.html`.

### Testes da API rodando (k6, ADR-023)

```bash
./load-tests/run.sh smoke     # funcional: fluxo de compra completo (também roda no CI)
./load-tests/run.sh load      # carga constante (RATE ações/s)
./load-tests/run.sh stress    # limite: degraus até sair do SLO
./load-tests/run.sh alerts    # dispara os alertas do Grafana de propósito
./load-tests/prod-like/prod-like.sh up && ./load-tests/prod-like/prod-like.sh stress   # na pilha de produção
```

Referência (notebook de dev):

| Ambiente | Mistura | Dentro do SLO (p95 < 500 ms) | Vazão máxima |
|---|---|---|---|
| Dev (H2, sem TLS) | Só leitura | ~1.600 ações/s | ~2.900 req/s |
| **Produção** (PostgreSQL, nginx com TLS) | Com login e compra | **~400 ações/s** | ~730 req/s |

O gargalo é a CPU (API e TLS). O estoque ficou íntegro sob saturação. O teste também revelou problemas que já
foram corrigidos e confirmados com o mesmo teste (0 erros 500):
- duas corridas que davam 500: o carrinho com o mesmo produto ao mesmo tempo e o **deadlock** entre checkout e
  cancelamento no PostgreSQL;
- o rate limit por IP da borda, que recusava clientes atrás do mesmo IP;
- a falta de limite de memória da API;
- os cabeçalhos de segurança duplicados.
Detalhes em [load-tests/README.md](load-tests/README.md).

## Documentação

- [Referência da API](docs/api-reference.md)
- [Arquitetura e modelo de domínio](docs/architecture.md)
- [Decisões de arquitetura (ADR)](docs/adr.md)
- [Bot de administração no Telegram](#bot-de-administração-no-telegram) — avisos de pedidos e gestão pelo chat (ADR-024)
- [Observabilidade](observability-stack/README.md) — OpenTelemetry + Grafana (ADR-022); alertas no Telegram com `observability-stack/telegram-setup.sh`
- [Guia de observabilidade](docs/instrunctions-cloud-impl-observability.md) — regras que não podem quebrar, verificação, manutenção e problemas conhecidos
- [Testes da API com k6](load-tests/README.md) — funcional, carga, estresse (capacidade e limite) e simulação de alertas (ADR-023)
- [Fluxo de desenvolvimento](docs/dev-workflow.md) — branches, worktrees, o aviso do `npm run dev` e como revisar PRs do Dependabot
- [Revisão de segurança e prontidão para produção](docs/security-review.md) — vulnerabilidades encontradas, checklist técnico e legal (LGPD, CDC, Marco Civil, Pix)
