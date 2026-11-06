# ShopEasy — Backend

API REST para e-commerce simples construída com Spring Boot 3.5.16 e Java 17.

## Tecnologias

| Dependência                     | Versão    | Uso                                      |
|---------------------------------|-----------|------------------------------------------|
| Spring Boot (parent/BOM)        | 3.5.16    | Spring Framework 6.2.19, Spring Security 6.5.11, Tomcat 10.1.55 |
| Spring Boot Starter Web         | 3.5.16    | Camada HTTP, serialização JSON           |
| Spring Boot Starter Security    | 3.5.16    | Filtro JWT, controle de acesso           |
| Spring Boot Starter Data JPA    | 3.5.16    | Repositórios, gerenciamento de entidades |
| Spring Boot Starter Validation  | 3.5.16    | Bean Validation nos DTOs de entrada      |
| Flyway Core                     | 11.7.2    | Migração de schema (V1–V5; portável H2/PostgreSQL) |
| com.auth0:java-jwt              | 4.6.2     | Geração e validação de tokens JWT        |
| H2 Database                     | 2.3.232   | Banco em memória (perfil `dev` e testes) |
| Lombok                          | 1.18.46   | Geração de boilerplate (getters, builders)|
| Springdoc OpenAPI               | 2.8.17    | Swagger UI (só no perfil `dev`)          |
| PostgreSQL JDBC                 | 42.7.11   | Banco do perfil `prod` (+ `flyway-database-postgresql` 11.7.2) |
| Spring Boot Starter Test        | 3.5.16    | JUnit 5, Mockito, MockMvc                |
| Spring Security Test            | 3.5.16    | `SecurityMockMvcRequestPostProcessors`   |
| JaCoCo                          | 0.8.16    | Relatório e mínimo de cobertura de testes |
| Maven Wrapper (`./mvnw`)        | 3.10.0    | Não é preciso instalar o Maven            |

## Estrutura de pacotes

```
com.sskings.ecommerce/
├── config/
│   ├── CorsConfig.java           # allowedOriginPatterns("*") para dev
│   ├── SecurityConfig.java       # .cors(), rotas públicas, /api/admin/** exige ROLE_ADMIN
│   ├── AdminSeeder.java          # Cria o admin inicial no startup (ADMIN_EMAIL/ADMIN_PASSWORD)
│   └── PaymentConfig.java        # Clock injetável, verificador do webhook, @EnableScheduling
├── payment/
│   ├── PixBrCode.java            # Gera o BR Code (EMV/Bacen) + CRC16-CCITT
│   ├── PixProperties.java        # app.pix.* validado no startup
│   ├── WebhookSignatureVerifier  # HMAC-SHA256, janela anti-replay de 5 min, comparação em tempo constante
│   └── PixExpirationJob.java     # @Scheduled: cancela pedidos com Pix vencido
├── security/
│   ├── JwtService.java           # Geração (HMAC256, 2h) e validação de tokens
│   └── JwtAuthFilter.java        # OncePerRequestFilter → popula SecurityContext
├── model/
│   ├── User.java                 # implements UserDetails, username = email, role, active
│   ├── Product.java
│   ├── Address.java              # ManyToOne User
│   ├── CartItem.java             # unique(user_id, product_id)
│   ├── Order.java                # OneToMany OrderItem
│   ├── OrderItem.java            # snapshot do nome e preço no momento da compra
│   ├── Payment.java              # OneToOne Order: status, amount, txid, pixPayload, expiresAt, paidAt
│   └── enums/
│       ├── OrderStatus.java      # PENDING | CONFIRMED | SHIPPED | DELIVERED | CANCELLED + canTransitionTo()
│       ├── PaymentStatus.java    # PENDING | PAID | EXPIRED | CANCELLED | REFUND_PENDING
│       └── Role.java             # CUSTOMER | ADMIN
├── dto/
│   ├── request/                  # Records com Bean Validation (@NotBlank, @Email…)
│   └── response/                 # Records com factory method ::from(Entity)
├── repository/                   # JpaRepository + queries derivadas e @Query JPQL
├── service/                      # Regras de negócio, @Transactional
│   └── admin/                    # Dashboard, produtos, pedidos e usuários (área admin)
├── controller/                   # @RestController, @AuthenticationPrincipal User
│   └── admin/                    # /api/admin/**
└── exception/
    ├── ResourceNotFoundException # → 404
    ├── BusinessException         # → 400
    ├── InvalidSignatureException # → 401 (webhook sem assinatura válida)
    ├── ErrorResponse             # Payload padronizado de erro
    └── GlobalExceptionHandler    # @RestControllerAdvice
```

## Configuração

Três arquivos em `src/main/resources/` (ADR-017):

| Arquivo | Quando | O que tem |
|---------|--------|-----------|
| `application.yml` | sempre | Configuração comum. **Nenhum segredo com valor padrão** (`${JWT_SECRET}` etc.) |
| `application-dev.yml` | perfil padrão (`./mvnw spring-boot:run`) | H2 em memória, Swagger e console H2 ligados, segredos e admin de dev **públicos e inseguros de propósito**, CORS `*`, MFA de admin opcional |
| `application-prod.yml` | `SPRING_PROFILES_ACTIVE=prod` | PostgreSQL, `forward-headers-strategy`, access log com 190 dias. Swagger e H2 desligados |

Em `prod`, o `ProductionConfigValidator` **impede o startup** com segredo ausente, curto ou conhecido (inclusive os
de dev e o que vazou no histórico do repositório), CORS aberto, banco H2 ou MFA de admin desligado.

| Variável (prod) | Obrigatória | Descrição |
|-----------------|-------------|-----------|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | sim | PostgreSQL (`jdbc:postgresql://host:5432/db`) |
| `JWT_SECRET` | sim | 32+ caracteres aleatórios (`openssl rand -base64 48`) |
| `JWT_EXPIRATION_MINUTES` | não (120) | Validade do token |
| `APP_ENCRYPTION_KEY` | sim | base64 de 32 bytes (`openssl rand -base64 32`); cifra os segredos de MFA |
| `CORS_ALLOWED_ORIGINS` | sim | Origens `https://` exatas do front, separadas por vírgula |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | sim | Admin inicial (senha 12+, letras e números) |
| `REQUIRE_ADMIN_MFA` | não (true) | Admin só usa `/api/admin/**` com MFA ativo |
| `TERMS_VERSION` | não | Versão dos termos/política gravada no aceite do cadastro |
| `PIX_*` | ver abaixo | Recebedor Pix e webhook |

Modelo pronto em [`../deploy/.env.prod.example`](../deploy/.env.prod.example).

### Pagamento Pix (`app.pix.*`)

| Variável                 | Padrão (dev)                         | O que é |
|--------------------------|--------------------------------------|---------|
| `PIX_KEY`                | `pix@shopeasy.example`               | Chave Pix da loja (email, CPF/CNPJ só dígitos, `+55…` ou aleatória). O padrão usa o domínio reservado `.example`: não existe no DICT, então nenhum teste paga para terceiros. **Defina a chave real para receber.** |
| `PIX_MERCHANT_NAME`      | `ShopEasy`                           | Nome do recebedor no app do banco (até 25 caracteres) |
| `PIX_MERCHANT_CITY`      | `Sao Paulo`                          | Cidade do recebedor (até 15) |
| `PIX_EXPIRATION_MINUTES` | `30`                                 | Validade do QR Code; vencido = pedido cancelado e estoque devolvido |
| `PIX_WEBHOOK_SECRET`     | `dev-pix-webhook-secret-change-me`   | Segredo HMAC do webhook do PSP. Em prod: 32+ caracteres novos; vazio desativa o webhook |
| `PIX_WEBHOOK_ALLOWED_IPS`| vazio (qualquer IP)                  | IPs/CIDRs do PSP autorizados a chamar o webhook (403 para os demais) |

A configuração é validada no startup (`PixProperties`): a aplicação não sobe com chave Pix inválida.

> Em desenvolvimento o admin padrão é `admin@shopeasy.com` / `admin123` (só no perfil `dev`).

## Rotas disponíveis

| Acesso    | Método | Rota                       |
|-----------|--------|----------------------------|
| Público   | POST   | `/api/auth/register`       |
| Público   | POST   | `/api/auth/login`          |
| JWT       | POST   | `/api/auth/logout`         |
| JWT       | GET/PUT | `/api/account`            |
| JWT       | GET    | `/api/account/data-export` |
| JWT       | POST   | `/api/account/delete`      |
| JWT       | POST   | `/api/account/mfa/{setup,enable,disable}` |
| Público   | GET    | `/api/products`            |
| Público   | GET    | `/api/products/{id}`       |
| JWT       | GET    | `/api/cart`                |
| JWT       | POST   | `/api/cart/items`          |
| JWT       | PUT    | `/api/cart/items/{id}`     |
| JWT       | DELETE | `/api/cart/items/{id}`     |
| JWT       | DELETE | `/api/cart`                |
| JWT       | GET    | `/api/addresses`           |
| JWT       | POST   | `/api/addresses`           |
| JWT       | DELETE | `/api/addresses/{id}`      |
| JWT       | POST   | `/api/orders/checkout`     |
| JWT       | GET    | `/api/orders`              |
| JWT       | GET    | `/api/orders/{id}`         |
| JWT       | GET    | `/api/orders/{id}/payment` |
| JWT       | POST   | `/api/orders/{id}/cancel`  |
| JWT       | POST   | `/api/orders/{id}/return`  |
| Público*  | POST   | `/api/payments/pix/webhook` |
| ADMIN     | POST   | `/api/admin/orders/{id}/payment/confirm` |
| ADMIN     | POST   | `/api/admin/orders/{id}/payment/refund` |
| ADMIN     | GET    | `/api/admin/audit`         |
| ADMIN     | GET    | `/api/admin/dashboard`     |
| ADMIN     | GET/POST | `/api/admin/products`    |
| ADMIN     | GET/PUT/DELETE | `/api/admin/products/{id}` |
| ADMIN     | GET    | `/api/admin/orders`        |
| ADMIN     | GET    | `/api/admin/orders/{id}`   |
| ADMIN     | PATCH  | `/api/admin/orders/{id}/status` |
| ADMIN     | GET    | `/api/admin/users`         |
| ADMIN     | GET/PATCH | `/api/admin/users/{id}` |
| ADMIN     | GET    | `/api/admin/users/{id}/orders` |

\* sem JWT, autenticado pela assinatura HMAC `X-Pix-Signature` (ver "Pagamento Pix" abaixo).

Veja exemplos completos em [`../docs/api-reference.md`](../docs/api-reference.md).

## Regras de negócio principais

- **Registro**: email único; senha criptografada com BCrypt antes de persistir.
- **Login**: retorna JWT com validade de 2 horas; `subject` = email do usuário.
- **Carrinho**: vinculado ao usuário autenticado; adicionar produto já existente incrementa
  quantidade; estoque é validado em cada operação.
- **Checkout**: valida estoque de todos os itens → decrementa estoque → persiste pedido com
  snapshot de nome/preço → cria a cobrança Pix → limpa carrinho. Tudo em uma única transação (`@Transactional`).
- **Pagamento Pix**: o BR Code ("copia e cola") é gerado no servidor (`PixBrCode`, padrão EMV do Bacen) com o
  total do pedido, a chave da loja e um `txid` aleatório de 25 caracteres. Pedido com Pix pendente só pode ser
  cancelado. Confirmação por webhook assinado do PSP (`PaymentService.handleWebhook`) ou manual pelo admin;
  pago → pedido `CONFIRMED`. Vencido (`PixExpirationJob`, a cada minuto, ou na consulta) → `EXPIRED`, pedido
  `CANCELLED` e estoque devolvido. Pix recebido para pedido cancelado → `REFUND_PENDING`.
- **Faturamento** (dashboard): soma só pedidos pagos e não cancelados.
- **Estoque**: baixa e devolução com `UPDATE` atômico no banco — checkouts simultâneos nunca vendem acima do estoque.
- **Sessão**: JWT com a versão da sessão (`ver`); logout, bloqueio, troca de papel e MFA revogam os tokens antigos.
  Após 5 senhas erradas a conta fica bloqueada por 15 min; login/cadastro limitados a 20/min por IP.
- **MFA (TOTP)**: opcional para clientes, obrigatório para admins em produção; segredo cifrado (AES-256-GCM).
- **Cliente**: cancela o pedido até o envio; pede devolução até 7 dias após a entrega (CDC art. 49); vê, corrige,
  exporta e exclui (anonimiza) os próprios dados (LGPD art. 18).
- **Auditoria**: `audit_logs` registra autenticação, MFA, pedidos, Pix, estornos, usuários, produtos e privacidade.
- **Endereço**: pertence ao usuário; toda operação verifica `address.user_id = current_user.id`.
- **Pedidos**: usuário acessa apenas seus próprios pedidos (filtro por `user_id` em todas as queries).
- **Papéis**: `CUSTOMER` (padrão no cadastro) e `ADMIN`. Rotas `/api/admin/**` exigem `ROLE_ADMIN`.
  Um admin inicial é criado na inicialização (`AdminSeeder`) com `ADMIN_EMAIL` / `ADMIN_PASSWORD`
  (padrão em dev: `admin@shopeasy.com` / `admin123` — troque em produção).
- **Admin — produtos**: CRUD completo; `DELETE` é exclusão lógica (`active = false`), preservando pedidos antigos.
- **Admin — pedidos**: fluxo `PENDING → CONFIRMED → SHIPPED → DELIVERED`, podendo pular etapas; a resposta traz
  `nextStatuses` (transições permitidas); cancelamento só antes do envio
  e devolve os itens ao estoque.
- **Admin — usuários**: bloquear/desbloquear e promover/rebaixar; usuário bloqueado não faz login e o token
  atual deixa de valer. O admin não pode alterar a própria conta.

## Testes

```bash
./mvnw test      # roda os testes e gera target/site/jacoco/index.html
./mvnw verify    # idem + falha se a cobertura ficar abaixo do mínimo (85% linhas / 80% branches)
```

**221 testes**, todos verdes. Cobertura (JaCoCo):

| Métrica   | Cobertura |
|-----------|-----------|
| Linhas    | **97,7%** (1028/1052) |
| Branches  | **93,4%** (395/423)   |
| Métodos   | 96,2% (308/320)       |

| Suite                          | Tipo              | Cenários cobertos |
|--------------------------------|-------------------|-------------------|
| `AuthServiceTest`              | Unit (Mockito)    | Registro (email normalizado, aceite de termos), duplicado, login, BCrypt também para email inexistente, bloqueio após N falhas (429 + Retry-After), bloqueio vencido, conta bloqueada, MFA (pedir código, código certo, reuso, errado), logout revoga |
| `ProductServiceTest`           | Unit (Mockito)    | Busca geral, filtro por nome, por id, produto inativo |
| `CartServiceTest`              | Unit (Mockito)    | Listar, adicionar, produto inexistente, sem estoque, remover, atualizar |
| `OrderServiceTest`             | Unit (Mockito)    | Carrinho vazio, estoque insuficiente (UPDATE atômico recusado), produto desativado, sucesso (cria cobrança Pix), cancelamento pelo cliente, arrependimento (prazo de 7 dias, repetido, antes da entrega) |
| `AccountServiceTest`           | Unit (Mockito)    | Perfil, exportação, exclusão/anonimização (endereços com pedido mantidos), senha/MFA na reautenticação, pedidos em andamento, MFA setup/enable/disable |
| `AuditServiceTest`             | Unit              | Ator explícito/implícito, IP, truncamento, busca, máscara de email |
| `TotpServiceTest`              | Unit              | Vetores oficiais da RFC 6238, Base32, janela ±1 passo, reuso, URI otpauth |
| `CryptoServiceTest`            | Unit              | AES-GCM ida e volta, IV aleatório, chave errada, texto adulterado, chave inválida |
| `RateLimitFilterTest`          | Unit              | Limite por IP e por balde, 429 + Retry-After, nova janela, rotas não limitadas, limpeza |
| `ProductionConfigValidatorTest`| Unit              | Configuração forte passa; valores de dev/vazados, CORS aberto, H2, senha fraca são recusados sem expor valores |
| `IpAllowlistTest`              | Unit              | IP exato, CIDR IPv4/IPv6, entradas inválidas |
| `SecurityHardeningIntegrationTest` | @SpringBootTest | **10 checkouts simultâneos para 3 unidades → 3 pedidos**; logout revoga tokens; força bruta → 429; token forjado com o segredo vazado → 401; admin só entra no /admin com MFA; exportação e exclusão de conta |
| `PaymentServiceTest`           | Unit (Mockito)    | Cria cobrança, consulta só do dono, expiração na consulta e no job (estoque devolvido), webhook pago / idempotente / valor errado / assinatura inválida / txid desconhecido / corpo inválido / após cancelamento, confirmação manual, cancelamento |
| `PixBrCodeTest`                | Unit              | CRC16 com o exemplo oficial do Bacen (`1D3D`), todos os campos EMV, limites de nome/cidade, chave longa, argumentos inválidos |
| `WebhookSignatureVerifierTest` | Unit              | HMAC válido, corpo/timestamp/segredo adulterados, replay fora da janela de 5 min, cabeçalhos ausentes, segredo não configurado |
| `PixExpirationJobTest`         | Unit              | Job delega a expiração |
| `PaymentControllerTest`        | @WebMvcTest       | Consulta do pagamento (login e dono), webhook público, 401 e 400 |
| `PixPaymentFlowIntegrationTest`| @SpringBootTest   | Flyway + H2 + segurança reais: checkout → BR Code → admin bloqueado até pagar → webhook sem/com assinatura → pedido `CONFIRMED`; confirmação manual; cancelamento devolve estoque |
| `AddressServiceTest`           | Unit (Mockito)    | Criar, usuário inexistente, listar, excluir, excluir endereço de outro usuário, endereço já usado em pedido |
| `AdminProductServiceTest`      | Unit (Mockito)    | Listar (com inativos), busca, criar, atualizar, exclusão lógica, 404 |
| `AdminOrderServiceTest`        | Unit (Mockito)    | Transição válida, pular etapas, inválida (SHIPPED → CANCELLED), estado final, cancelamento devolve estoque e cancela o Pix, Pix pendente bloqueia o avanço, Pix pago libera, confirmação manual, `nextStatuses`, filtros, 404 |
| `AdminUserServiceTest`         | Unit (Mockito)    | Bloquear, promover, auto-alteração proibida, busca, pedidos do usuário |
| `AdminDashboardServiceTest`    | Unit (Mockito)    | Agregação dos indicadores |
| `JwtServiceTest`               | Unit              | Round-trip com versão, expiração configurável, outro segredo, sem versão, outro issuer, lixo |
| `OrderStatusTest`              | Unit parametrizado| Todas as transições de status e `nextStatuses()` |
| `UserTest`                     | Unit              | Papéis → authorities, conta bloqueada |
| `AdminSeederTest`              | Unit (Mockito)    | Cria admin, não duplica |
| `AuthControllerTest`           | @WebMvcTest       | Register 201, login 200, credenciais inválidas, body inválido, campos longos, senha fraca e termos não aceitos, 429, logout |
| `AccountControllerTest`        | @WebMvcTest       | Rotas de conta exigem login (401), perfil, exportação como anexo, exclusão, MFA |
| `JwtAuthFilterTest`            | Unit              | Admin sem MFA perde ROLE_ADMIN quando obrigatório |
| `ProductControllerTest`        | @WebMvcTest       | GET público, detalhe, rota protegida sem auth → 403 |
| `CartControllerTest`           | @WebMvcTest       | GET autenticado, 403 sem auth, POST/PUT/DELETE item |
| `AddressAndOrderControllerTest`| @WebMvcTest       | CRUD de endereço, UF inválida, campo longo demais, exclusão de endereço em uso, checkout, pedidos, cancelar/devolver, 409 de lock otimista, 404, 500 genérico |
| `AdminProductControllerTest`   | @WebMvcTest       | 403 sem auth e para cliente, 201 para admin, validação, 204 |
| `AdminControllersTest`         | @WebMvcTest       | JWT real (admin/cliente/bloqueado/inválido/revogado → 401/403), pedidos (com `nextStatuses`), estorno, auditoria, usuários, dashboard |
| `CorsPreflightTest`            | @WebMvcTest       | Preflight `OPTIONS` para PATCH (admin), GET, PUT, DELETE, POST; método desconhecido recusado |

Lacunas conhecidas: em `CartService`, adicionar um produto que já está no carrinho (soma de
quantidades), atualizar para uma quantidade acima do estoque e `clearCart`; o `DELETE /api/cart`
do `CartController`; e os `@PrePersist` de `User`/`Order` (só rodam com JPA real).

### Padrão de teste para services
```java
@ExtendWith(MockitoExtension.class)   // sem contexto Spring
class FooServiceTest {
    @Mock FooRepository fooRepository;
    @InjectMocks FooService fooService;

    @Test void cenario_condicao_resultado() { ... }
}
```

### Padrão de teste para controllers
```java
@WebMvcTest(FooController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class FooControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean FooService fooService;
    @MockBean UserRepository userRepository;  // necessário pelo JwtAuthFilter
    @MockBean JwtService jwtService;

    // Injetar usuário autenticado sem JWT real:
    mockMvc.perform(get("/api/foo").with(user(mockUser())));
}
```

## Adicionando um novo recurso

1. Crie a migração `V{n}__descricao.sql` em `src/main/resources/db/migration/`.
2. Crie a entidade em `model/` com `@Entity`, Lombok e `@PrePersist` se necessário.
3. Crie o `Repository` estendendo `JpaRepository<Entidade, Long>`.
4. Crie DTOs de request (record + Bean Validation) e response (record + `::from` factory).
5. Implemente o `Service` com `@Transactional` e lance `ResourceNotFoundException` / `BusinessException`.
6. Implemente o `Controller` com `@RestController` e `@AuthenticationPrincipal User user`.
   Se for exclusivo de admin, coloque-o em `controller/admin` sob `/api/admin/...` — a regra de
   acesso já é aplicada pela `SecurityConfig`.
7. Escreva testes unitários para o service e slice tests para o controller; rode `./mvnw verify`
   para garantir que a cobertura mínima continua sendo atingida.
8. Atualize [`../docs/api-reference.md`](../docs/api-reference.md) com os novos endpoints.

Armadilhas que já aconteceram neste projeto:

- **SQL portável:** a migração roda em H2 (dev/testes) e PostgreSQL (prod). Use
  `GENERATED BY DEFAULT AS IDENTITY`, não `AUTO_INCREMENT` (ADR-017).
- **`@Version` começa `null`:** com valor inicial (`= 0L`) o Spring Data trata a entidade nova como existente,
  faz `merge` e duplica inserts em cascata.
- **Verbo HTTP novo:** inclua-o em `CorsConfig.ALLOWED_METHODS`. Faltar `PATCH` quebrou o admin (ADR-014);
  o `CorsPreflightTest` acusa.
- **Ação sensível:** registre na auditoria (`AuditService.record`; use `recordIndependent` para falhas que dão
  rollback).
- **Configuração nova obrigatória em produção:** sem valor padrão no `application.yml`, valor de exemplo no
  `application-dev.yml`, verificação no `ProductionConfigValidator` e linha no `../deploy/.env.prod.example`.
