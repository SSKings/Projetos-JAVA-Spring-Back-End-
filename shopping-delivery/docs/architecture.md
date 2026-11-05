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
                                   * snapshot imutável
```

Relacionamentos:
- `User` → `CartItem`: 1:N — um usuário tem muitos itens no carrinho
- `User` → `Address`: 1:N — um usuário tem múltiplos endereços
- `User` → `Order`: 1:N — um usuário tem múltiplos pedidos
- `Order` → `OrderItem`: 1:N — um pedido tem múltiplos itens
- `CartItem` → `Product`: N:1 — constraint UNIQUE(user_id, product_id) evita duplicatas
- `OrderItem` → `Product`: N:1 — apenas para referência; nome e preço são copiados

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
JwtAuthFilter (OncePerRequestFilter)
  │  Extrai token do header Authorization
  │  Valida token via JwtService
  │  Busca User no banco (UserRepository)
  │  Popula SecurityContext
  ▼
  │  (ignora usuários com active = false)
  ▼
SecurityConfig (chain)
  │  CORS (preflight OPTIONS liberado)
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
```

### Ciclo de vida do pedido

```
PENDING ──► CONFIRMED ──► SHIPPED ──► DELIVERED
   │            │
   └────────────┴──► CANCELLED   (devolve itens ao estoque)
```

Regra implementada em `OrderStatus.canTransitionTo()`; qualquer outra transição retorna 400.

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

### H2 em desenvolvimento
Elimina a necessidade de Docker ou instância de banco para rodar o projeto localmente. Flyway executa as mesmas migrações que rodariam em produção, mantendo paridade.

---

## Estrutura de pacotes

```
com.sskings.ecommerce/
├── config/          # CORS, Security, AdminSeeder
├── security/        # JWT (geração, validação, filtro)
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
