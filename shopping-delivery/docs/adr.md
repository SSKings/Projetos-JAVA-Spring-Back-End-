# Architecture Decision Records

Registro das principais decisões técnicas tomadas no projeto ShopEasy, com contexto e consequências.

---

## ADR-001 — Autenticação stateless com JWT

**Status:** Aceito

**Contexto**  
A API precisa autenticar requisições sem manter estado no servidor. O frontend React vive em uma origem diferente (porta 5173) da API (porta 8080).

**Decisão**  
Usar JWT (HMAC256) com validade de 2 horas, gerado pela biblioteca `com.auth0:java-jwt`. O token é retornado no corpo do login e enviado pelo cliente no header `Authorization: Bearer <token>`. O Spring Security valida o token em cada requisição via `JwtAuthFilter extends OncePerRequestFilter`.

**Consequências**
- Sem sessão no servidor — escalável horizontalmente sem sticky sessions.
- O token expira em 2 horas; não há refresh token (simplificação consciente para MVP).
- Revogar um token antes do vencimento requer lista de bloqueio (não implementado).
- O filtro armazena o objeto `User` completo no `SecurityContext`, não apenas o subject — evita uma segunda query no controller.

---

## ADR-002 — Sem entidade Cart separada

**Status:** Aceito

**Contexto**  
Modelagens clássicas de e-commerce criam uma entidade `Cart` com FK para `User`, e `CartItem` com FK para `Cart`. Isso adiciona uma tabela extra para representar uma relação 1:1 entre `User` e `Cart`.

**Decisão**  
`CartItem` referencia `User` diretamente via `user_id`. Uma constraint `UNIQUE(user_id, product_id)` garante que o mesmo produto não aparece duas vezes no carrinho do mesmo usuário.

**Consequências**
- Schema mais simples (menos um JOIN para buscar o carrinho).
- Upsert é feito com `findByUserIdAndProductId`: se existe, incrementa; senão, insere.
- Se no futuro precisarmos de metadata no carrinho (ex: cupom de desconto), precisaríamos adicionar a entidade `Cart` — mudança de schema.

---

## ADR-003 — Snapshot de preço e nome em OrderItem

**Status:** Aceito

**Contexto**  
`OrderItem` poderia simplesmente guardar uma FK para `Product`. Mas se o preço do produto for alterado no futuro, o valor histórico do pedido mudaria.

**Decisão**  
`OrderItem` armazena `product_name VARCHAR(150)` e `unit_price DECIMAL(10,2)` como cópias no momento da compra, além de `product_id` (que pode ser nulo se o produto for removido).

**Consequências**
- Histórico de pedidos é imutável e correto independente de mudanças no catálogo.
- Leve duplicação de dados (nome e preço existem também em `Product`).
- Relatórios que precisam do nome/preço do produto em pedidos históricos não precisam de JOIN com `products`.

---

## ADR-004 — Records Java para DTOs

**Status:** Aceito

**Contexto**  
DTOs de request e response precisam ser objetos simples com campos, getters e (no caso de request) suporte a Bean Validation.

**Decisão**  
Usar Java Records para todos os DTOs. DTOs de resposta têm um factory method `static XxxResponse from(Entity e)`. DTOs de request têm anotações `@NotBlank`, `@Email`, etc. nos componentes do record.

**Consequências**
- Sem Lombok nos DTOs — records são nativos do Java 16+.
- Imutabilidade garantida por design.
- O factory method `from` localiza a lógica de mapeamento perto do DTO, não no service.
- Records não suportam herança; se DTOs compartilharem campos, usa-se composição ou interfaces.

---

## ADR-005 — H2 em memória para desenvolvimento

**Status:** Aceito

**Contexto**  
O projeto deve ser fácil de rodar localmente sem dependências externas. Para produção, o banco será PostgreSQL.

**Decisão**  
`application.yml` configura H2 em memória (`jdbc:h2:mem:ecommercedb`). Flyway executa V1 (schema) e V2 (seed) a cada restart. Para produção, as mesmas variáveis de ambiente sobrescrevem o datasource para PostgreSQL.

**Consequências**
- `mvn spring-boot:run` funciona imediatamente, sem Docker.
- Dados são perdidos a cada restart — aceitável em dev.
- Flyway garante que o schema de dev e produção são criados pelo mesmo script.
- O console H2 está disponível em `/h2-console` para inspeção durante desenvolvimento.

---

## ADR-006 — Checkout atômico em um único @Transactional

**Status:** Aceito

**Contexto**  
O checkout envolve múltiplas operações: validar estoque, decrementar estoque, criar pedido, criar itens do pedido e limpar o carrinho. Qualquer falha parcial deixaria o banco em estado inconsistente.

**Decisão**  
Todo o fluxo de checkout está em um único método `@Transactional` em `OrderService`. Se qualquer passo lançar uma exceção, o Spring reverte todas as operações.

**Consequências**
- Consistência garantida: nunca haverá estoque decrementado sem pedido criado, ou pedido criado sem carrinho limpo.
- O método `checkout` é mais longo do que o ideal (valida, decrementa, cria, persiste, limpa). Poderia ser decomposto, mas os passos são fortemente acoplados pela transação.
- Testes de unidade fazem mock do repository e verificam o comportamento em cada cenário de falha.

---

## ADR-007 — @WebMvcTest para testes de controller

**Status:** Aceito

**Contexto**  
Testes de controller precisam verificar mapeamento de rotas, serialização/deserialização JSON e integração com Spring Security — mas não precisam subir o contexto completo da aplicação.

**Decisão**  
Usar `@WebMvcTest(XxxController.class)` combinado com `@Import({SecurityConfig.class, JwtAuthFilter.class})`. Autenticação é injetada via `SecurityMockMvcRequestPostProcessors.user(mockUser())` — sem gerar JWTs reais nos testes.

**Consequências**
- Testes rodam em ~300ms em vez de segundos (contexto parcial).
- `@MockBean UserRepository` e `@MockBean JwtService` são obrigatórios porque `JwtAuthFilter` os usa.
- Testar rotas públicas não exige `.with(user(...))`.
- Testar 401/403 é feito omitindo o `.with(user(...))` — a cadeia de segurança real bloqueia a requisição.
- O caminho real do `JwtAuthFilter` (token válido, inválido, usuário bloqueado) é coberto em
  `AdminControllersTest` mockando `JwtService.validateToken` e `UserRepository.findByEmail`.

---

## ADR-008 — Frontend como SPA separada (Vite + React)

**Status:** Aceito

**Contexto**  
O backend poderia servir templates (Thymeleaf), mas o projeto visa separação clara de responsabilidades e práticas modernas.

**Decisão**  
Frontend é uma SPA independente em React 19 + TypeScript rodando em Vite (porta 5173). Comunica com a API via Axios. Em produção, seria servida por um CDN ou servidor de arquivos estáticos.

**Consequências**
- CORS configurado no backend (`CorsConfig.java`) para aceitar qualquer origem em dev. A cadeia do
  Spring Security também precisa de `.cors(Customizer.withDefaults())`; sem isso o *preflight* `OPTIONS`
  recebia 403 e o cadastro falhava no navegador (embora funcionasse via curl/Postman).
- O frontend nunca calcula totais — o backend é a fonte de verdade; o frontend exibe o que a API retorna.
- Dois processos para rodar localmente (backend + frontend).
- `ProtectedRoute` no React redireciona para `/login` rotas que requerem autenticação.

---

## ADR-009 — Controle de acesso por papel (CUSTOMER / ADMIN)

**Status:** Aceito

**Contexto**
A loja precisava de alguém para gerenciar catálogo, pedidos e clientes. Até então todo usuário
recebia `ROLE_CUSTOMER` fixo e não havia como administrar nada além do seed SQL.

**Decisão**
- Coluna `role` (`CUSTOMER` | `ADMIN`) e `active` em `users` (migração `V3`).
- `User.getAuthorities()` devolve `ROLE_<role>`; rotas `/api/admin/**` exigem `hasRole("ADMIN")`
  na `SecurityConfig` — autorização centralizada por URL, sem `@PreAuthorize` espalhado.
- Endpoints de admin ficam em `controller/admin` + `service/admin`, separados dos endpoints do cliente,
  com DTOs próprios (`AdminProductResponse`, `AdminOrderResponse`, `UserResponse`).
- O primeiro admin é criado no startup pelo `AdminSeeder` a partir de `ADMIN_EMAIL`/`ADMIN_PASSWORD`,
  em vez de um hash BCrypt fixo numa migração.

**Consequências**
- Bloquear um usuário (`active = false`) tem efeito imediato: o login é recusado e o `JwtAuthFilter`
  ignora tokens de usuários desabilitados, mesmo que ainda não tenham expirado.
- Um admin não pode bloquear nem rebaixar a própria conta (evita ficar sem nenhum admin por engano).
- O papel vai na resposta de login; o front-end o usa só para UX — a segurança real é do back-end.

---

## ADR-010 — Máquina de estados do pedido e exclusão lógica de produtos

**Status:** Aceito

**Contexto**
Com o admin alterando pedidos e produtos, era preciso evitar estados inconsistentes
(ex.: pedido entregue voltando a pendente) e não quebrar o histórico de pedidos.

**Decisão**
- `OrderStatus.canTransitionTo()` define o fluxo `PENDING → CONFIRMED → SHIPPED → DELIVERED`;
  `CANCELLED` só a partir de `PENDING`/`CONFIRMED`. Transições inválidas → 400.
- Cancelar devolve as quantidades ao estoque na mesma transação.
- `DELETE /api/admin/products/{id}` apenas marca `active = false`; o produto some da vitrine,
  mas `order_items.product_id` continua válido.

**Consequências**
- O front-end espelha as transições (`nextOrderStatuses`) para mostrar apenas opções válidas,
  mas o back-end continua sendo a fonte de verdade.
- Produtos nunca são apagados fisicamente; a listagem de admin mostra ativos e inativos.

---

## ADR-011 — Cobertura de testes medida e exigida no build

**Status:** Aceito

**Contexto**
O back-end tinha testes, mas sem medição; o front-end não tinha nenhum teste.

**Decisão**
- Back-end: JaCoCo (`mvn verify`) com mínimo de **85% de linhas e 80% de branches**.
- Front-end: Vitest + Testing Library + `@vitest/coverage-v8` (`npm run coverage`) com mínimo de
  **90% de linhas/statements e 85% de branches/funções**.
- O CI executa ambos; uma queda de cobertura abaixo do mínimo quebra o build.

**Consequências**
- Testes de página usam `vi.mock` nos módulos de `src/api/` — sem servidor real.
- A camada HTTP (`axios.ts`) é testada com um *adapter* falso, cobrindo o interceptor de token e o
  logout automático em 401/403.
