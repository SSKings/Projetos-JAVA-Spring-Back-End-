# ShopEasy — Backend

API REST para e-commerce simples construída com Spring Boot 3.3.7 e Java 17.

## Tecnologias

| Dependência                     | Versão    | Uso                                      |
|---------------------------------|-----------|------------------------------------------|
| Spring Boot Starter Web         | 3.3.7     | Camada HTTP, serialização JSON           |
| Spring Boot Starter Security    | 3.3.7     | Filtro JWT, controle de acesso           |
| Spring Boot Starter Data JPA    | 3.3.7     | Repositórios, gerenciamento de entidades |
| Spring Boot Starter Validation  | 3.3.7     | Bean Validation nos DTOs de entrada      |
| Flyway Core                     | 3.3.7     | Migração de schema (V1 init, V2 seed, V3 papéis) |
| com.auth0:java-jwt              | 4.4.0     | Geração e validação de tokens JWT        |
| H2 Database                     | runtime   | Banco em memória para desenvolvimento   |
| Lombok                          | opcional  | Geração de boilerplate (getters, builders)|
| Springdoc OpenAPI               | 2.5.0     | Swagger UI automático                    |
| Spring Boot Starter Test        | 3.3.7     | JUnit 5, Mockito, MockMvc                |
| Spring Security Test            | 3.3.7     | `SecurityMockMvcRequestPostProcessors`   |
| JaCoCo                          | 0.8.12    | Relatório e mínimo de cobertura de testes |

## Estrutura de pacotes

```
com.sskings.ecommerce/
├── config/
│   ├── CorsConfig.java           # allowedOriginPatterns("*") para dev
│   ├── SecurityConfig.java       # .cors(), rotas públicas, /api/admin/** exige ROLE_ADMIN
│   └── AdminSeeder.java          # Cria o admin inicial no startup (ADMIN_EMAIL/ADMIN_PASSWORD)
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
│   └── enums/
│       ├── OrderStatus.java      # PENDING | CONFIRMED | SHIPPED | DELIVERED | CANCELLED + canTransitionTo()
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
    ├── ErrorResponse             # Payload padronizado de erro
    └── GlobalExceptionHandler    # @RestControllerAdvice
```

## Configuração

`src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:ecommercedb
  flyway:
    locations: classpath:db/migration   # V1__init.sql + V2__seed.sql + V3__add_user_role_and_status.sql

security:
  jwt:
    secret: <troque em produção>
```

**Para produção**, sobrescreva via variáveis de ambiente:
```
SPRING_DATASOURCE_URL=jdbc:postgresql://host/db
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
SECURITY_JWT_SECRET=<segredo longo e aleatório>
ADMIN_EMAIL=admin@suaempresa.com
ADMIN_PASSWORD=<senha forte>
```

> Em desenvolvimento o admin padrão é `admin@shopeasy.com` / `admin123`.

## Rotas disponíveis

| Acesso    | Método | Rota                       |
|-----------|--------|----------------------------|
| Público   | POST   | `/api/auth/register`       |
| Público   | POST   | `/api/auth/login`          |
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
| ADMIN     | GET    | `/api/admin/dashboard`     |
| ADMIN     | GET/POST | `/api/admin/products`    |
| ADMIN     | GET/PUT/DELETE | `/api/admin/products/{id}` |
| ADMIN     | GET    | `/api/admin/orders`        |
| ADMIN     | GET    | `/api/admin/orders/{id}`   |
| ADMIN     | PATCH  | `/api/admin/orders/{id}/status` |
| ADMIN     | GET    | `/api/admin/users`         |
| ADMIN     | GET/PATCH | `/api/admin/users/{id}` |
| ADMIN     | GET    | `/api/admin/users/{id}/orders` |

Veja exemplos completos em [`../docs/api-reference.md`](../docs/api-reference.md).

## Regras de negócio principais

- **Registro**: email único; senha criptografada com BCrypt antes de persistir.
- **Login**: retorna JWT com validade de 2 horas; `subject` = email do usuário.
- **Carrinho**: vinculado ao usuário autenticado; adicionar produto já existente incrementa
  quantidade; estoque é validado em cada operação.
- **Checkout**: valida estoque de todos os itens → decrementa estoque → persiste pedido com
  snapshot de nome/preço → limpa carrinho. Tudo em uma única transação (`@Transactional`).
- **Endereço**: pertence ao usuário; toda operação verifica `address.user_id = current_user.id`.
- **Pedidos**: usuário acessa apenas seus próprios pedidos (filtro por `user_id` em todas as queries).
- **Papéis**: `CUSTOMER` (padrão no cadastro) e `ADMIN`. Rotas `/api/admin/**` exigem `ROLE_ADMIN`.
  Um admin inicial é criado na inicialização (`AdminSeeder`) com `ADMIN_EMAIL` / `ADMIN_PASSWORD`
  (padrão em dev: `admin@shopeasy.com` / `admin123` — troque em produção).
- **Admin — produtos**: CRUD completo; `DELETE` é exclusão lógica (`active = false`), preservando pedidos antigos.
- **Admin — pedidos**: fluxo `PENDING → CONFIRMED → SHIPPED → DELIVERED`; cancelamento só antes do envio
  e devolve os itens ao estoque.
- **Admin — usuários**: bloquear/desbloquear e promover/rebaixar; usuário bloqueado não faz login e o token
  atual deixa de valer. O admin não pode alterar a própria conta.

## Testes

```bash
./mvnw test      # roda os testes e gera target/site/jacoco/index.html
./mvnw verify    # idem + falha se a cobertura ficar abaixo do mínimo (85% linhas / 80% branches)
```

**106 testes**, todos verdes. Cobertura (JaCoCo):

| Métrica   | Cobertura |
|-----------|-----------|
| Linhas    | **94,8%** (361/381) |
| Branches  | **87,5%** (56/64)   |
| Métodos   | 91,8% (135/147)     |

| Suite                          | Tipo              | Cenários cobertos |
|--------------------------------|-------------------|-------------------|
| `AuthServiceTest`              | Unit (Mockito)    | Registro, e-mail duplicado, login, senha errada, conta bloqueada, não encontrado |
| `ProductServiceTest`           | Unit (Mockito)    | Busca geral, filtro por nome, por id, produto inativo |
| `CartServiceTest`              | Unit (Mockito)    | Listar, adicionar, produto inexistente, sem estoque, remover, atualizar |
| `OrderServiceTest`             | Unit (Mockito)    | Carrinho vazio, estoque insuficiente, sucesso, endereço ausente, listagem |
| `AddressServiceTest`           | Unit (Mockito)    | Criar, usuário inexistente, listar, excluir, excluir endereço de outro usuário |
| `AdminProductServiceTest`      | Unit (Mockito)    | Listar (com inativos), busca, criar, atualizar, exclusão lógica, 404 |
| `AdminOrderServiceTest`        | Unit (Mockito)    | Transição válida/inválida, cancelamento devolve estoque, filtros, 404 |
| `AdminUserServiceTest`         | Unit (Mockito)    | Bloquear, promover, auto-alteração proibida, busca, pedidos do usuário |
| `AdminDashboardServiceTest`    | Unit (Mockito)    | Agregação dos indicadores |
| `JwtServiceTest`               | Unit              | Round-trip, token adulterado, outro segredo, expirado, lixo |
| `OrderStatusTest`              | Unit parametrizado| Todas as transições de status |
| `UserTest`                     | Unit              | Papéis → authorities, conta bloqueada |
| `AdminSeederTest`              | Unit (Mockito)    | Cria admin, não duplica |
| `AuthControllerTest`           | @WebMvcTest       | Register 201, login 200, credenciais inválidas, body inválido |
| `ProductControllerTest`        | @WebMvcTest       | GET público, detalhe, rota protegida sem auth → 403 |
| `CartControllerTest`           | @WebMvcTest       | GET autenticado, 403 sem auth, POST/PUT/DELETE item |
| `AddressAndOrderControllerTest`| @WebMvcTest       | CRUD de endereço, UF inválida, checkout, pedidos, 404, 500 genérico |
| `AdminProductControllerTest`   | @WebMvcTest       | 403 sem auth e para cliente, 201 para admin, validação, 204 |
| `AdminControllersTest`         | @WebMvcTest       | JWT real (admin/cliente/bloqueado/inválido), pedidos, usuários, dashboard |

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
