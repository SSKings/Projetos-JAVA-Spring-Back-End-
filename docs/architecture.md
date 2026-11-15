# ShopEasy — Arquitetura e Modelo de Domínio

## Modelo de entidades

```
┌─────────────┐       ┌──────────────┐       ┌─────────────┐
│    users    │       │  cart_items  │       │  products   │
│─────────────│       │──────────────│       │─────────────│
│ id (PK)     │◄──┐   │ id (PK)      │   ┌──►│ id (PK)     │
│ name        │   │   │ user_id (FK) │   │   │ name        │
│ email       │   └───│ product_id   │───┘   │ description │
│ password    │       │ quantity     │       │ image_url   │
│ role        │       │ UNIQUE       │       │ price       │
│ active      │       │              │       │             │
└─────────────┘       │ (user+prod)  │       │ stock_qty   │
       │              └──────────────┘       │ active      │
       │                                     └─────────────┘
       │
       │   ┌─────────────┐
       │   │  addresses  │
       │   │─────────────│
       └──►│ id (PK)     │
       │   │ user_id (FK)│
       │   │ street      │
       │   │ number      │
       │   │ complement  │
       │   │ neighborhood│
       │   │ city        │
       │   │ state       │
       │   │ zip_code    │
       │   └─────────────┘
       │
       │   ┌─────────────┐       ┌─────────────────┐
       │   │   orders    │       │   order_items   │
       │   │─────────────│       │─────────────────│
       └──►│ id (PK)     │◄──┐   │ id (PK)         │
           │ user_id (FK)│   │   │ order_id (FK)   │
           │ address_id  │   └───│ product_id (FK) │
           │ status      │       │ product_name *  │
           │ total       │       │ unit_price *    │
           │ created_at  │       │ quantity        │
           └─────────────┘       └─────────────────┘
                  ▲                * snapshot imutável
                  │ 1:1
           ┌──────┴────────┐
           │   payments    │   (V4)
           │───────────────│
           │ id (PK)       │
           │ order_id (FK, │
           │   UNIQUE)     │
           │ method        │   PIX
           │ status        │   PENDING | PAID | EXPIRED | CANCELLED | REFUND_PENDING
           │ amount        │   = orders.total
           │ txid (UNIQUE) │   25 alfanuméricos, SecureRandom
           │ pix_payload   │   BR Code "copia e cola"
           │ end_to_end_id │   id do Pix no SPI (vem do PSP)
           │ confirmed_by  │   WEBHOOK | ADMIN
           │ created_at    │
           │ expires_at    │
           │ paid_at       │
           └───────────────┘
```

Relacionamentos:
- `User` → `CartItem`: 1:N — um usuário tem muitos itens no carrinho
- `User` → `Address`: 1:N — um usuário tem múltiplos endereços
- `User` → `Order`: 1:N — um usuário tem múltiplos pedidos
- `Order` → `OrderItem`: 1:N — um pedido tem múltiplos itens
- `CartItem` → `Product`: N:1 — constraint UNIQUE(user_id, product_id) evita duplicatas
- `OrderItem` → `Product`: N:1 — apenas para referência; nome e preço são copiados
- `Order` → `Payment`: 1:1 — `orders` não mudou; `payments.order_id` é UNIQUE

---

## Camadas da aplicação

```
┌────────────────────────────────────────────────────────────┐
│                         Controller                          │
│  Recebe HTTP, extrai principal (@AuthenticationPrincipal), │
│  delega ao Service, devolve ResponseEntity                  │
└───────────────────────────┬────────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────────┐
│                          Service                            │
│  Regras de negócio, @Transactional, lança exceções de      │
│  domínio (ResourceNotFoundException, BusinessException)     │
└──────────────┬────────────────────────────┬────────────────┘
               │                            │
┌──────────────▼──────────────┐  ┌─────────▼──────────────────┐
│         Repository           │  │     Domain Model (Entity)   │
│  JpaRepository + JPQL        │  │  @Entity, Lombok, @Pre*     │
│  queries derivadas           │  │  User implements UserDetails│
└──────────────────────────────┘  └────────────────────────────┘
```

### Responsabilidades por camada

**Controller**
- Mapeia rotas HTTP (`@RestController`, `@RequestMapping`)
- Valida body de entrada via `@Valid`
- Injeta o usuário autenticado com `@AuthenticationPrincipal User user`
- Nunca contém lógica de negócio

**Service**
- Toda operação de escrita é `@Transactional`
- Leituras usam `@Transactional(readOnly = true)`
- Lança `ResourceNotFoundException` → 404 e `BusinessException` → 400
- É a única camada que conhece regras como "carrinho vazio bloqueia checkout"

**Repository**
- Estende `JpaRepository<Entity, Long>`
- Queries derivadas (`findByEmailAndActive`) para casos simples
- `@Query` JPQL com `JOIN FETCH` para prevenir N+1 em coleções

**Security**
```
Request
  │
  ▼
RateLimitFilter (login, cadastro, webhook: N/min por IP → 429)
  │
  ▼
JwtAuthFilter (OncePerRequestFilter)
  │  Extrai token do header Authorization
  │  Valida token via JwtService
  │  Busca User no banco (UserRepository)
  │  Popula SecurityContext
  ▼
  │  (ignora usuários com active = false e tokens com versão antiga — revogação)
  │  (admin sem MFA, com REQUIRE_ADMIN_MFA: autentica sem ROLE_ADMIN)
  ▼
SecurityConfig (chain)
  │  CORS (preflight OPTIONS liberado; métodos em CorsConfig.ALLOWED_METHODS, inclui PATCH)
  │  Público: POST /api/auth/**, GET /api/products/**
  │  ADMIN:   /api/admin/**  → hasRole("ADMIN")
  │  Demais:  authenticated()
  ▼
Controller
```

---

## Área administrativa

```
controller/admin/                 service/admin/
├── AdminDashboardController  →  AdminDashboardService   (contagens + faturamento)
├── AdminProductController    →  AdminProductService     (CRUD, exclusão lógica)
├── AdminOrderController      →  AdminOrderService       (listagem global, mudança de status)
└── AdminUserController       →  AdminUserService        (busca, bloqueio, papel)

telegram/  (opcional, ADR-024: TELEGRAM_ADMIN_BOT_ENABLED=true)
├── TelegramBotPoller      long polling (getUpdates) numa thread própria
├── TelegramAdminBot       comandos e botões → os MESMOS serviços acima, com o admin no SecurityContext
├── TelegramOrderNotifier  OrderEvent (após o commit) → aviso aos admins
└── TelegramClient         Bot API via RestClient; token nunca nos logs
```

Os serviços publicam `event/OrderEvent` (novo pedido, Pix confirmado pelo PSP, cancelamento pelo cliente, devolução,
Pix expirado). Sem o bot ligado, ninguém escuta e nada muda.

### Ciclo de vida do pedido

```
Só para frente, podendo pular etapas:

  PENDING ──► CONFIRMED ──► SHIPPED ──► DELIVERED   (final)
     │            │
     └────────────┴──► CANCELLED                     (final; só antes do envio, devolve o estoque)

  Atalhos permitidos: PENDING → SHIPPED, PENDING → DELIVERED, CONFIRMED → DELIVERED
```

Regra implementada em `OrderStatus.canTransitionTo()`; qualquer outra transição retorna 400.
`OrderStatus.nextStatuses()` vai em `AdminOrderResponse.nextStatuses` e alimenta o seletor do admin (ADR-014).

---

## Pagamento Pix

```
Checkout (mesma transação)
  └── PaymentService.createPixCharge(order)
        ├── txid aleatório (SecureRandom, 25 chars)
        ├── PixBrCode.build(chave, nome, cidade, total, txid, "Pedido N")   ← EMV/Bacen + CRC16
        └── Payment PENDING, expiresAt = agora + PIX_EXPIRATION_MINUTES

Front-end (PixPayment) ── GET /api/orders/{id}/payment a cada 5 s ──► mostra QR / copia e cola / contador

Confirmação
  ├── PSP ─► POST /api/payments/pix/webhook
  │          WebhookSignatureVerifier: HMAC-SHA256(timestamp.corpo), ±5 min, comparação em tempo constante
  │          valor == amount?  ── não ─► 400 (nada muda)
  │          PENDING ─► PAID, pedido PENDING ─► CONFIRMED
  │          EXPIRED/CANCELLED ─► REFUND_PENDING (dinheiro a devolver)
  │          PAID/REFUND_PENDING ─► 200 sem mudança (idempotente)
  └── Admin ─► POST /api/admin/orders/{id}/payment/confirm  (Pix estático, conferido no extrato)

Expiração
  PixExpirationJob (@Scheduled, 60 s) e a própria consulta do cliente:
  PENDING vencido ─► EXPIRED, pedido ─► CANCELLED, StockService.restore()
```

`Order.canTransitionTo()` combina a máquina de estados com o pagamento: enquanto o Pix não está pago, o único
destino possível é `CANCELLED` (decisões em ADR-016). O `Clock` é um bean, então os testes controlam "agora" (expiração e janela do
webhook) sem `sleep`.

---

## Fluxo de checkout (transação única)

```
POST /api/orders/checkout
  │
  ├── Busca carrinho do usuário
  │     └── 400 se vazio
  │
  ├── Busca endereço e verifica ownership
  │     └── 404 se não encontrado
  │
  ├── Para cada CartItem:
  │     ├── Verifica product.stockQuantity >= item.quantity
  │     │     └── 400 se insuficiente
  │     └── Decrementa product.stockQuantity
  │
  ├── Cria Order com snapshot de items:
  │     OrderItem {
  │       productName = product.name,   // cópia imutável
  │       unitPrice   = product.price,  // cópia imutável
  │       quantity    = cartItem.qty
  │     }
  │
  ├── Calcula order.total = sum(item.unitPrice * item.quantity)
  │
  ├── PaymentService.createPixCharge → cobrança Pix PENDING
  │
  └── deleteAllByUserId → carrinho limpo
       └── Commit da transação
```

---

## Decisões de design importantes

### Sem entidade Cart
O carrinho é representado diretamente por `CartItem` com `user_id`. Uma entidade `Cart` seria uma tabela extra sem comportamento próprio. A constraint `UNIQUE(user_id, product_id)` cumpre o papel de garantir que não há duplicatas.

### DTOs como Java Records
Records eliminam boilerplate de getters/construtores e são imutáveis por natureza. O factory method `static ResponseDTO from(Entity entity)` mantém a conversão perto do DTO.

### Snapshot em OrderItem
Preço e nome do produto são copiados no momento da compra. Se o administrador alterar o preço de um produto, pedidos históricos permanecem com os valores originais.

### Segurança em camadas (ADR-017)

| Camada | Mecanismo |
|--------|-----------|
| Configuração | Perfis `dev`/`prod`; `ProductionConfigValidator` impede startup inseguro |
| Borda | nginx: TLS, cabeçalhos de segurança (fonte única: os da API são escondidos), `limit_req` (login 10/min, API 100/s por IP); API sem porta pública e com `mem_limit` (ADR-019) |
| Autenticação | BCrypt; JWT com `ver` (revogação via `User.tokenVersion`); bloqueio após 5 falhas, idêntico para email inexistente e contando a reautenticação; MFA TOTP com segredo AES-256-GCM |
| Autorização | URL (`/api/admin/**` → `ROLE_ADMIN`); consultas sempre filtradas pelo usuário (`findByIdAndUserId`) |
| Dados | Estoque com `UPDATE` atômico; `@Version` em `Order`/`Payment`; validação de entrada espelhando as colunas; carrinho com trava do usuário (`findByIdForUpdate`); estoque sempre travado em ordem de id do produto (sem deadlock); violação de restrição ou vítima de trava → 409 |
| Rastreabilidade | `audit_logs` + access log do Tomcat (190 dias) |
| Entrada | Corpo ≤ 64 KB (`RequestSizeLimitFilter`, 413); erros de requisição com código próprio e sem stack trace (ADR-021) |
| Dependências | Versões do BOM sobrescritas quando há CVE; `check-dependency-cves.py` (OSV) e `npm audit` no CI (ADR-021) |
| Observabilidade | Actuator: `health` público sem detalhes; `metrics`/`prometheus` só ADMIN; resto não exposto (ADR-022) |

### Observabilidade (ADR-022)
Traces, métricas e logs com OpenTelemetry, opcionais e sem dependência no código de negócio:
- **Back:** agente Java (`scripts/run-backend.sh`) instrumenta HTTP, Spring Data, Hibernate e JDBC, e coloca
  `trace_id`/`span_id` no MDC (padrão de log em `application.yml`).
- **Front:** `src/observability/` liga, se `VITE_OTEL_ENABLED=true`, as instrumentações XHR (axios) e fetch, que
  enviam o `traceparent` W3C à API. Por isso o trace começa no navegador.
- **Stack:** `observability-stack/` (Collector → Tempo/Loki/Prometheus → Grafana). Ver o README dessa pasta.
- **Dashboards e alertas:** 2 dashboards e 12 alertas provisionados de arquivo (disponibilidade, segurança,
  recursos), com notificação opcional no **Telegram** (`telegram-setup.sh`). As métricas de negócio derivam das
  métricas HTTP. O contrato é o template da rota (`http_route`), validado contra os controllers no CI.
- **Testes da API rodando (ADR-023):** `load-tests/` (k6), com:
  - `smoke` funcional no CI;
  - `load`, `stress` (capacidade e limite) e `alerts` (simulações que disparam os alertas).

```
audit_logs (V5): id, created_at, actor_id, actor_email, action, target_type, target_id, details, ip
users (+V5):     token_version, failed_login_attempts, locked_until, mfa_enabled, mfa_secret (cifrado),
                 mfa_last_step, terms_version, terms_accepted_at, anonymized_at
orders (+V5):    version, delivered_at, return_requested_at, return_reason
payments (+V5):  version, refunded_at
```

### Endereço usado em pedido não pode ser excluído
`orders.address_id` é FK para `addresses`. `AddressService.delete` consulta `existsByAddressId` e lança
`BusinessException("Address is linked to an order")` (400) em vez de deixar a constraint estourar como 500.
A tela **Minha conta → Endereços** mostra a mensagem traduzida.

### Validação em duas camadas
Os records `RegisterRequest` e `AddressRequest` têm `@Size` iguais às colunas do banco. O front-end aplica as
mesmas regras (e algumas de UX, como CEP `00000-000`, UF válida e confirmação de senha) em
`src/utils/validation.ts` antes de chamar a API — ver ADR-012.

### Índices do banco (V8)

O PostgreSQL **não cria índice para chave estrangeira**: até a V7 só a auditoria tinha índice, e as consultas por
cliente, status ou data varriam a tabela inteira. A V8 cria um índice para cada consulta frequente, escolhido a partir
do **SQL real do Hibernate** (capturado com `logging.level.org.hibernate.SQL=DEBUG` nos testes de integração) e medido
num PostgreSQL 16 (mesma imagem da produção) com 100 mil clientes, 1 milhão de pedidos, 2,5 milhões de itens e
2 milhões de eventos de auditoria — mediana de 5 execuções de `EXPLAIN ANALYZE`:

| Consulta | Quando roda | Antes | Depois | Índice |
|---|---|---|---|---|
| Histórico de pedidos do cliente (com itens) | Minha conta → Pedidos | 2.807 ms | 0,97 ms | `idx_orders_user_status`, `idx_order_items_order` |
| Pedidos pendentes do cliente | todo checkout | 39 ms | 0,21 ms | `idx_orders_user_status` |
| Unidades pendentes do produto | todo checkout, por item | 81 ms | 0,17 ms | `idx_orders_user_status`, `idx_order_items_order` |
| Pix abandonados em 24 h | todo checkout | 39 ms | 0,65 ms | `idx_orders_user_status` |
| Pedidos em andamento | excluir conta | 39 ms | 0,27 ms | `idx_orders_user_status` |
| Painel: pedidos por status (página) | admin | 178 ms | 0,26 ms | `idx_orders_status_created` |
| Painel: todos os pedidos (página) | admin | 1.123 ms | 0,28 ms | `idx_orders_created` |
| Pix vencidos (com o job em dia) | **a cada minuto** | 72 ms | 0,22 ms | `idx_payments_status_expires` |
| Endereços do cliente | checkout, Minha conta | 12 ms | 0,38 ms | `idx_addresses_user` |
| Endereço já usado em pedido (pior caso) | excluir endereço/conta | 66 ms | 0,15 ms | `idx_orders_address` |
| Eventos do titular (exportação LGPD) | exportar dados | 77 ms | 0,68 ms | `idx_audit_logs_actor_created` |
| Anonimizar a auditoria | excluir conta | 166 ms | 1,8 ms | `idx_audit_logs_actor_created` |

**Custo:** inserir pedidos ficou ~9% mais lento (50 mil inserções: 4,1 s → 4,5 s, ~0,008 ms por pedido); os índices
ocupam ~215 MB sobre ~1,1 GB de dados nesse volume; criar todos na V8 levou ~5 s.

**Sem índice, de propósito (medido):** busca de produto por nome (7 ms com 5 mil produtos — `LIKE '%x%'` só usa índice
com `pg_trgm`, que vale a pena acima de ~50 mil produtos); retenção das origens confiáveis (29 ms, uma vez por dia — um
índice pesaria em todo login); carrinho (já usa o índice do `UNIQUE (user_id, product_id)`).

**Banco grande já em produção:** `CREATE INDEX` bloqueia escritas na tabela enquanto cria. Com milhões de linhas, crie
antes, fora do horário, com `CREATE INDEX CONCURRENTLY` e os mesmos nomes da V8 — ela usa `IF NOT EXISTS` e só confirma.

**Consulta nova?** Rode-a com `EXPLAIN ANALYZE` num banco com volume (o roteiro está no PR da V8) antes de decidir
se precisa de índice. `DatabaseIndexesIntegrationTest` garante que os índices existem, nas colunas e na ordem certas.

### H2 em desenvolvimento
Elimina a necessidade de Docker ou instância de banco para rodar o projeto localmente. Flyway executa as mesmas migrações que rodariam em produção, mantendo paridade.

---

## Estrutura de pacotes

```
com.sskings.ecommerce/
├── config/          # CORS, Security, AdminSeeder, PaymentConfig, ProductionConfigValidator
├── event/           # OrderEvent (fatos do pedido, ouvidos após o commit)
├── telegram/        # Bot de administração no Telegram (opcional, ADR-024)
├── payment/         # PixBrCode, PixProperties, WebhookSignatureVerifier, IpAllowlist, PixExpirationJob
├── security/        # JwtService, JwtAuthFilter, RateLimitFilter, TotpService, CryptoService, SecurityProperties
├── model/           # Entidades JPA + enums
│   └── enums/
├── dto/
│   ├── request/     # Records de entrada (Bean Validation)
│   └── response/    # Records de saída (factory ::from)
├── repository/      # JpaRepository + JPQL
├── service/         # Lógica de negócio
│   └── admin/       # Regras da área administrativa
├── controller/      # Camada HTTP
│   └── admin/       # /api/admin/** (ROLE_ADMIN)
└── exception/       # Exceções de domínio + handler global
```
