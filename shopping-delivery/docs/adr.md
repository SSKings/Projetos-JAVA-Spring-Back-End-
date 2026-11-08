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
  *(Atualizado pelo ADR-014: o pedido pode pular etapas para frente.)*
- Cancelar devolve as quantidades ao estoque na mesma transação.
- `DELETE /api/admin/products/{id}` apenas marca `active = false`; o produto some da vitrine,
  mas `order_items.product_id` continua válido.

**Consequências**
- ~~O front-end espelha as transições (`nextOrderStatuses`)~~ — substituído pelo ADR-014: a API envia
  `nextStatuses` e o front-end não duplica mais a regra.
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

---

## ADR-012 — Validação de formulários no front-end espelhando o Bean Validation

**Status:** Aceito

**Contexto**
O cadastro só verificava o tamanho da senha e o endereço do checkout dependia de `required` do HTML.
Valores maiores que as colunas do banco (ex.: rua com 200 caracteres) passavam pelo `@Valid` e
quebravam no `INSERT` com erro 500.

**Decisão**
- Back-end: `@Size` em `RegisterRequest` e `AddressRequest` com os mesmos limites das colunas
  (`users`, `addresses`); senha limitada a 72 caracteres (limite do BCrypt).
- Front-end: funções puras `validateRegister` / `validateAddress` em `src/utils/validation.ts`, um hook
  `useValidatedForm` (erro aparece no *blur* ou no primeiro envio) e o componente `FormField`
  (`aria-invalid` + `aria-describedby`). Máscaras de CEP e telefone; o formulário usa `noValidate`
  para que as mensagens sejam sempre as nossas, em português.
- Um único `AddressForm` é usado no checkout e em **Minha conta → Endereços**.

**Consequências**
- O usuário recebe o erro no campo, antes da requisição; o back-end continua sendo a garantia final.
- Mudou um limite no banco? Atualize a migração, o `@Size` e `LIMITS` em `validation.ts`.

---

## ADR-013 — Área "Minha conta" calculada a partir de `GET /api/orders`

**Status:** Aceito

**Contexto**
O cliente só tinha uma lista simples de pedidos, acessível por um link escondido no mobile. Faltavam filtros,
acompanhamento do status, visão por produto comprado e gestão de endereços.

**Decisão**
- Layout `AccountLayout` com abas **Meus pedidos** (`/orders`), **Histórico de compras** (`/purchases`) e
  **Endereços** (`/addresses`); `/account` redireciona para `/orders`.
- Resumo, filtros por grupo de status, busca e paginação são feitos no cliente sobre a resposta de
  `GET /api/orders` (funções puras em `src/utils/orders.ts`). Nenhum endpoint novo.
- `OrderItemResponse` passou a expor `productId`: o histórico de compras agrupa por produto (e não pelo
  nome, que é snapshot e pode mudar) e linka para a página dele.
- Total gasto e histórico de compras **ignoram pedidos cancelados**, a mesma regra do faturamento no dashboard.

**Consequências**
- Simples e sem custo extra no back-end enquanto um cliente tem poucas dezenas de pedidos.
- Se o volume crescer, a evolução natural é paginar `GET /api/orders` no servidor (como já é feito em
  `/api/admin/orders`) e mover o agregado por produto para uma consulta própria.

---

## ADR-014 — Correções na alteração de status de pedidos (CORS, etapas e `nextStatuses`)

**Status:** Aceito

**Contexto**
Na área administrativa, alterar o status de um pedido mostrava "Não foi possível conectar ao servidor".
A causa: `CorsConfig` liberava `GET, POST, PUT, DELETE, OPTIONS`, mas não `PATCH`. O navegador recusava o
*preflight* (403) de `PATCH /api/admin/orders/{id}/status` — e de `PATCH /api/admin/users/{id}` — e o axios
não recebia resposta. Os testes `@WebMvcTest` não simulavam o preflight, então nada falhou no CI.
Além disso, o seletor mostrava só um passo à frente (de `PENDING` só "Confirmado" ou "Cancelado"), sem
explicar o resto, e a regra de transições estava duplicada no front-end.

**Decisão**
- `PATCH` incluído em `CorsConfig.ALLOWED_METHODS`; novo `CorsPreflightTest` envia `OPTIONS` com
  `Origin` e `Access-Control-Request-Method` para cada verbo usado pelo front-end (falha com 403 sem a correção).
- O pedido pode **pular etapas para frente** (ex.: retirada na loja: `PENDING → DELIVERED`). Continua proibido
  voltar, sair de `DELIVERED`/`CANCELLED` e cancelar depois do envio.
- `AdminOrderResponse.nextStatuses` traz as transições permitidas (`OrderStatus.nextStatuses()`); o front-end
  lista **todos** os status no seletor, marca o atual e desabilita os não permitidos.
- Cancelar pede confirmação (devolve estoque e é irreversível); o seletor fica desabilitado enquanto salva;
  sucesso e erro aparecem na tela, com a mensagem de transição recusada traduzida.

**Consequências**
- Uma única fonte da regra (o enum no back-end); mudar o fluxo não exige mexer no front-end.
- Todo verbo HTTP novo na API precisa entrar em `ALLOWED_METHODS` — o `CorsPreflightTest` acusa a falta.

---

## ADR-015 — Acesso pela rede local: Vite em todas as interfaces e URL da API derivada do host

**Status:** Aceito

**Contexto**
Outros computadores da rede não conseguiam usar a aplicação. Duas causas:
1. O Vite, por padrão, escuta só em `localhost` (no Linux ficava em `[::1]:5173`) — conexões vindas da rede
   eram recusadas.
2. `axios.ts` tinha `baseURL: 'http://localhost:8080'` fixo. Mesmo carregando a página, o navegador do outro
   computador chamaria a API na **própria** máquina e mostraria "Não foi possível conectar ao servidor".

O back-end já escutava em todas as interfaces (sem `server.address`) e o CORS já aceitava qualquer origem.

**Decisão**
- `vite.config.ts`: `server.host: true` e `preview.host: true` (portas fixas com `strictPort`).
- `src/api/baseUrl.ts`: `resolveApiBaseUrl()` usa `VITE_API_BASE_URL` se definida; senão
  `<protocolo>//<host da página>:8080`. Função pura, testada em `baseUrl.test.ts`.
- `.env.example` documenta a variável.

**Alternativa considerada**
Proxy do Vite para `/api` (uma porta só no firewall e sem CORS). Descartada por ora: só funciona com o dev
server/preview do Vite; a derivação pelo host funciona igual em qualquer forma de servir o build.

**Consequências**
- Portas 5173 e 8080 precisam estar liberadas no firewall da máquina que roda o projeto.
- Ambiente de dev fica visível na rede, incluindo o console H2 e o admin padrão — usar credenciais próprias
  (`ADMIN_EMAIL`/`ADMIN_PASSWORD`) em redes compartilhadas.

---

## ADR-016 — Pagamento via Pix: BR Code no servidor, webhook assinado e confirmação manual

**Status:** Aceito

**Contexto**
A loja não cobrava nada: o checkout só criava o pedido. Era preciso um pagamento real via Pix, com QR Code,
"copia e cola" e o pedido avançando conforme o pagamento. Confirmar um Pix automaticamente exige um PSP
(banco/gateway com API Pix e webhook) — e o projeto ainda não tem contrato/credenciais com nenhum.

**Decisão**
- **BR Code gerado no servidor** (`PixBrCode`), no padrão EMV QRCPS-MPM do Manual de Iniciação do Pix (Bacen):
  chave da loja, valor = `orders.total`, nome/cidade sanitizados, `txid` aleatório (SecureRandom, 25 chars) e
  CRC16-CCITT. Testado contra o exemplo oficial do manual (CRC `1D3D`). O app do banco paga para a chave
  configurada em `PIX_KEY`. O front-end só desenha o QR a partir desse texto — nunca monta payload nem valor.
- **Confirmação** por dois caminhos:
  1. `POST /api/payments/pix/webhook` no formato que um PSP usa: assinatura `HMAC-SHA256(secret, timestamp.corpo)`,
     janela de 5 min contra replay, comparação em tempo constante, verificação do valor exato e idempotência.
  2. Confirmação manual pelo admin, após conferir valor e identificador no extrato (Pix estático, sem PSP).
- **Estado do pedido ligado ao pagamento**: Pix pago → `CONFIRMED`; Pix pendente bloqueia qualquer avanço (só
  cancelar); vencido → `EXPIRED` + pedido `CANCELLED` + estoque devolvido (`PixExpirationJob` a cada minuto e
  também na consulta); Pix recebido para pedido cancelado → `REFUND_PENDING`.
- Faturamento e "total gasto" passam a contar só pedidos pagos.
- `expiresInSeconds` é calculado no servidor para o contador não depender do relógio do cliente.
- Chave padrão de dev `pix@shopeasy.example` (domínio reservado, não cadastrável no DICT): nenhum teste paga para
  a conta de um terceiro.

**Alternativas consideradas**
- *Integrar já um PSP específico* (Efí, Mercado Pago, Asaas…): exige conta e credenciais; o webhook genérico
  deixa a troca isolada em um adaptador que traduz a notificação do PSP para `PixWebhookRequest`.
- *Gerar o QR no back-end como PNG (ZXing)*: mais uma dependência e payload maior; o SVG no navegador é nítido
  e o conteúdo continua vindo do servidor.

**Consequências**
- Funciona hoje sem PSP (confirmação manual) e fica pronto para um PSP (webhook).
- Com Pix estático, um cliente pode pagar valor diferente digitando à mão; o webhook recusa valor divergente e o
  admin deve conferir o valor antes de confirmar. Um QR dinâmico (cobrança `cob` via API do PSP) eliminaria isso.
- Estorno (`REFUND_PENDING`) ainda é feito fora do sistema, pelo app do banco.

---

## ADR-017 — Endurecimento de segurança (correções da revisão de segurança)

**Status:** Aceito

**Contexto**
A revisão (`security-review.md`) confirmou, com o sistema rodando:
- tokens de admin forjáveis com o segredo publicado no repositório;
- venda acima do estoque com checkouts simultâneos;
- força bruta sem limite;
- enumeração de usuários;
- dependências fora de suporte.

**Decisão**
- **Configuração por perfil:** `application.yml` sem segredos, `application-dev.yml` (padrão) e `application-prod.yml`. `ProductionConfigValidator` impede o startup em `prod` com:
  - segredo ausente, curto ou conhecido;
  - CORS aberto;
  - H2;
  - MFA de admin desligado.
- **Estoque:** baixa e devolução com `UPDATE` atômico condicionado ao estoque, em vez de ler, comparar e gravar. `@Version` em `Order` e `Payment`. A versão começa `null` para que o Spring Data use `persist` em entidades novas.
- **Sessão:**
  - JWT carrega `ver` = `User.tokenVersion`; logout, bloqueio, troca de papel e MFA incrementam a versão e todos os tokens antigos deixam de valer;
  - expiração configurável;
  - entry point devolve **401**; acesso negado devolve **403**. O front faz logout só em 401.
- **Força bruta:**
  - `RateLimitFilter` por IP (janela de 1 min, em memória, por instância) e `limit_req` no nginx;
  - bloqueio da conta por 15 min após 5 falhas, persistido em `users`;
  - BCrypt executado também para email inexistente.
- **MFA:** TOTP (RFC 6238) implementado sem dependência, segredo cifrado com AES-256-GCM (`APP_ENCRYPTION_KEY`) e reuso de código bloqueado (`mfa_last_step`). Com `REQUIRE_ADMIN_MFA=true`, admin sem MFA perde `ROLE_ADMIN` até ativar.
- **Auditoria:** `audit_logs`. Eventos de falha são gravados em transação própria (`REQUIRES_NEW`) para sobreviver ao rollback.
- **Dependências:** Spring Boot 3.5.16 (linha 3.x, migração de baixo risco) e Dependabot.

**Consequências**
- O projeto não sobe mais em produção "por acidente" com configuração de dev.
- O rate limit em memória vale por instância. Ao escalar horizontalmente, o nginx/gateway (ou Redis) é a camada de verdade.
- O bloqueio por tentativas permite que alguém bloqueie a conta de outra pessoa por 15 min. É o equilíbrio usual; o rate limit por IP reduz o abuso.
- A mensagem de cadastro "email já em uso" permanece até existir verificação de email.

---

## ADR-018 — Direitos do titular (LGPD) e do consumidor (CDC) no produto

**Status:** Aceito

**Contexto**
O cliente não conseguia ver, corrigir, exportar nem excluir os próprios dados. Também não conseguia cancelar um pedido nem exercer o direito de arrependimento. Não havia aceite de termos nem identificação do fornecedor.

**Decisão**
- **`/api/account`:** perfil (GET/PUT), `data-export` (JSON com perfil, endereços, pedidos e eventos de segurança), `delete` (com senha e MFA) e MFA.
- **Exclusão = anonimização:**
  - nome, email, telefone e senha são substituídos e a conta desativada;
  - carrinho e endereços sem pedido são apagados;
  - pedidos e os endereços usados neles são mantidos sem vínculo com a pessoa (obrigação legal/fiscal e defesa; LGPD art. 16, I e art. 7º, VI);
  - a auditoria troca o email do titular pelo anonimizado;
  - com pedido em andamento, a exclusão é recusada.
- **Cadastro:** exige `acceptTerms`; gravam-se `terms_version` (`TERMS_VERSION`) e `terms_accepted_at`.
- **Pedidos:**
  - `POST /api/orders/{id}/cancel` enquanto não enviado (estoque devolvido; Pix pendente → cancelado; Pix pago → `REFUND_PENDING`);
  - `POST /api/orders/{id}/return` até 7 dias após `delivered_at` (gravado quando o admin marca `DELIVERED`), motivo opcional;
  - admin registra o estorno (`REFUNDED`).
- **Front-end:** abas *Dados e privacidade* e *Segurança*, rodapé com `VITE_COMPANY_*` e modelos de política, termos e trocas escritos a partir do que o sistema faz.

**Consequências**
- Os textos são modelos. Precisam de revisão jurídica e dos dados reais da empresa, e as páginas avisam isso enquanto houver campo sem preencher.
- O estorno em si (devolução do Pix) continua fora do sistema até integrar um PSP.

---

## ADR-019 — Implantação: nginx na mesma origem, PostgreSQL e containers sem root

**Status:** Aceito

**Decisão**
- `shopping-delivery/deploy/docker-compose.prod.yml`: `db` (PostgreSQL 16, sem porta exposta), `api` (perfil `prod`, read-only, sem capabilities, `no-new-privileges`) e `web` (nginx sem root).
- O nginx termina o TLS, redireciona HTTP para HTTPS, serve o build do React e faz proxy de `/api` para a API. O front usa `VITE_API_BASE_URL=/` (mesma origem): sem CORS na prática e `connect-src 'self'` na CSP.
- Cabeçalhos: HSTS, CSP estrita, X-Frame-Options, Referrer-Policy, Permissions-Policy (`security-headers.inc`, incluído em cada `location` porque o nginx não herda `add_header`).
- `X-Forwarded-For` recebe `$remote_addr`: o cliente não forja o IP usado no rate limit e na auditoria.
- Access log da API em volume próprio, com retenção de 190 dias (Marco Civil, art. 15).
- mTLS do webhook Pix pronto (comentado) para quando houver PSP.

**Consequências**
- Para publicar, é preciso:
  - um domínio;
  - certificado (Let's Encrypt ou do provedor) em `deploy/certs`;
  - um `.env.prod` preenchido (nunca versionado);
  - backups do volume `db-data` (ou banco gerenciado).
- Fixe as imagens por digest no pipeline de release; o Dependabot acompanha as tags (minor/patch, ver ADR-020).

---

## ADR-020 — Política de atualização de dependências e verificações obrigatórias no CI

**Status:** Aceito

**Contexto**
Dois incidentes no mesmo dia (`dev-workflow.md`):
1. O Dependabot abriu 14 PRs, um por dependência. Entre os mergeados estava o **Tailwind 3 → 4** (major), que
   quebrou `npm run build` e o CSS do `npm run dev`. O CI ficou verde: os testes usam `css: false` e o job do
   front não fazia build.
2. As validações dos formulários "sumiram" localmente porque o checkout estava numa branch criada antes do
   merge que as trouxe. O código estava certo e nada avisava.

**Decisão**
- **Dependabot:** atualizações minor/patch **agrupadas** (um PR por ecossistema: Maven, npm, Docker, Actions) e
  **majors ignoradas**. Major é migração e entra num PR próprio, com revisão.
- **CI do front:** `npm ci` → `tsc -b` → `lint` → `coverage` → **`npm run build`**.
- **Tailwind mantido na v3** (`^3.4.19`) até uma migração planejada (`npx @tailwindcss/upgrade` + revisão visual).
- **`npm run dev`** roda antes `scripts/dev-preflight.mjs`, que avisa quando a branch não tem commits de
  `origin/main` ou está atrás do upstream. Trabalho paralelo é feito em `git worktree`.

**Consequências**
- Menos PRs, cada um com risco menor. Majors (Spring Boot 4, Java 25, Node 26, Tailwind 4…) ficam visíveis só
  quando alguém decide migrar; revise a lista periodicamente.
- Quebra de build do front aparece no PR, não depois do merge.
- Depois de cada `git pull`, rode `npm ci`: o `node_modules` local não acompanha o `package-lock.json` sozinho.

---

## ADR-021 — Revisão de segurança 2: CVEs de dependências transitivas, dev sem segredos públicos e respostas uniformes

**Status:** Aceito

**Contexto**
Uma segunda revisão (`security-review.md`, seção 6) encontrou:
- CVEs em dependências que o Spring Boot gerencia (Tomcat, Jackson, driver PostgreSQL), que o Dependabot não vê;
- o ambiente de dev (perfil padrão, acessível pela rede) com segredo JWT e senha de admin públicos;
- o bloqueio por tentativas revelando contas;
- reautenticação sem limite;
- corpo de requisição sem limite;
- erros comuns respondendo 500 com stack trace.

**Decisão**
- **Versões gerenciadas:** sobrescritas por propriedade no `pom.xml` enquanto o Spring Boot não traz a correção.
  `scripts/check-dependency-cves.py` (OSV) e `npm audit` rodam no CI; exceções só com motivo em `cve-allowlist.txt`.
- **Dev:** segredo JWT aleatório a cada start e senha do admin gerada e mostrada no log. Nada que dê acesso fica
  versionado, nem mesmo no perfil de desenvolvimento.
- **Bloqueio:** mesma regra (5 falhas / 15 min) para emails inexistentes, e a reautenticação conta como tentativa de login.
- **Corpo:** limite de 64 KB na API (`RequestSizeLimitFilter`), além do 1 MB do nginx.
- **Erros de requisição:** códigos próprios (404, 405, 415, 400, 413) sem log de erro; o handler genérico fica para
  bugs de verdade.

**Consequências**
- O CI passa a depender da API do OSV, que exige rede; uma CVE nova pode quebrar um PR sem relação com ela. É
  intencional: a correção é atualizar ou justificar.
- Em dev é preciso ler a senha do admin no log a cada start (ou definir `ADMIN_PASSWORD`).
- O bloqueio de email inexistente fica em memória e vale por instância; com várias réplicas, use a borda ou um
  store compartilhado.

---

## ADR-022 — Observabilidade com OpenTelemetry e Grafana LGTM

**Status:** Aceito

**Contexto**
Não havia como seguir uma requisição do navegador até o SQL, nem métricas além do access log. A especificação
`docs/instrunctions-cloud-impl-observability.md` propõe OpenTelemetry no front e no back, um Collector e a stack
Grafana (Tempo, Loki, Prometheus). Seguida ao pé da letra, ela não funcionava: Tempo com chave inválida, logs sem
pipeline, agente na porta errada, front sem instrumentação de XHR (o axios usa XHR), CORS da API recusando o
`traceparent`. Também abria riscos: imagens e agente `latest` sem verificação, portas abertas para a rede com
Grafana anônimo, Actuator com detalhes públicos.

**Decisão**
- **Arquitetura da especificação**, em `observability-stack/` (Collector → Tempo, Loki por OTLP, Prometheus;
  Grafana com datasources de uid fixo e correlação trace ↔ log). Versões fixas e portas presas a `127.0.0.1`.
- **Backend:** agente Java OpenTelemetry (`scripts/run-backend.sh`). A versão fica em `pom.xml`
  (`otel-javaagent.version`) e o agente é baixado pelo Maven com checksum obrigatório (`-C`). O código não depende
  do agente: sem ele a API roda igual. O padrão de log inclui `trace_id`/`span_id`.
- **Actuator:** `health` público, só com UP/DOWN. Detalhes, `metrics` e `prometheus` exigem ADMIN (e MFA quando
  exigido, porque o `JwtAuthFilter` não dá `ROLE_ADMIN` sem ele). Os demais endpoints não são expostos.
- **CORS:** `traceparent` e `tracestate` em `CorsConfig.ALLOWED_HEADERS`.
- **Frontend:** instrumentação XHR + fetch com `BatchSpanProcessor`, **opt-in** (`VITE_OTEL_ENABLED=true`). O SDK é
  carregado por import dinâmico (chunk separado) antes do render. Os spans vão para `/otlp` (proxy do Vite, mesma
  origem). O `traceparent` só vai para a API desta aplicação (`resolveApiBaseUrl`), nunca para terceiros.

**Consequências**
- Um clique no front mostra o trace inteiro no Grafana, até a consulta SQL; as linhas de log apontam para o trace.
- Cobertura: `ActuatorSecurityIntegrationTest`, `CorsPreflightTest` (trace headers) e
  `src/observability/*.test.ts` (configuração e propagação do `traceparent` em XHR).
- É uma stack de **desenvolvimento**: em dev, os logs (inclusive a senha de admin gerada, ADR-021) vão para o Loki.
  Produção pede um collector atrás do nginx e um backend com autenticação. A CSP atual (`connect-src 'self'`) já
  impede enviar telemetria a outra origem.
- O bundle principal não muda com a instrumentação desligada. Ligada, carrega mais ~25 KB gzip.
- Dashboards versionados em `observability-stack/dashboards/` (API e negócio/segurança), provisionados no start do
  Grafana e validados no CI (`scripts/test_grafana_provisioning.py`). As métricas de negócio derivam das métricas HTTP,
  sem código novo no backend. O contrato é o template da rota (`http_route`), e o CI acusa rota renomeada.
- 12 regras de alerta versionadas em `observability-stack/alerting/` (disponibilidade, segurança, recursos), na
  pasta "ShopEasy — Alertas" e validadas no mesmo teste. Notificações opcionais no **Telegram**
  (`docker-compose.telegram.yml`, assistente `telegram-setup.sh`), com o token e o chat ID num `.env` não
  versionado. Um entrypoint valida os dois e gera o contact point: a interpolação do Grafana transforma o chat ID em
  número e impede o start.

---

## ADR-023 — Testes da API rodando com k6 (funcional, carga, estresse e alertas)

**Status:** Aceito

**Contexto**
Os testes do backend (JUnit/MockMvc) validam o código, mas não a API rodando como um cliente a vê. Não havia como
saber a capacidade da API, onde ela satura, nem como disparar os alertas (ADR-022) de propósito para conferir as
notificações.

**Decisão**
- **k6** (Grafana), rodando via Docker (`load-tests/run.sh`, versão fixada em `K6_IMAGE`, sem instalação), com
  quatro roteiros:
  - `smoke`: fluxo de compra completo com 23 verificações;
  - `load`: taxa constante com a mistura de uso de uma loja;
  - `stress`: degraus até sair do SLO, com o limite calculado no fim;
  - `alerts`: força bruta, varredura e webhook forjado.
- **Taxa de chegada constante** (`constant-arrival-rate`), e não um número fixo de usuários em laço: clientes reais
  não esperam a API responder para chegar. Assim a saturação aparece como latência e como iterações descartadas.
- O **`smoke` roda no CI** contra a API subida a partir do jar do `verify`. Os outros roteiros são validados com
  `k6 inspect`.
- Os testes usam só usuários criados por eles (`*@carga.teste`), no máximo 15 por execução, por causa do rate limit
  do cadastro. As escritas não esgotam o estoque, e o pedido do `smoke` é cancelado.

**Consequências**
- Referência em notebook de dev (H2, k6 na mesma máquina): ~1.600 ações/s dentro do SLO e ~2.900 req/s de vazão
  máxima, com degradação suave (latência sobe, quase sem erro). O gargalo é a CPU. Os números servem para comparar
  versões; dimensionar a produção pede um ambiente igual ao de produção.
- O CI ganha cerca de 1 minuto e passa a pegar quebras de contrato da API (status, campos, fluxo) que os testes de
  unidade não veem.
- Achado ao escrever o `smoke`: `itemCount` do carrinho conta **linhas** (produtos distintos), não unidades. É o
  comportamento esperado pelo front; o teste documenta isso.
- **Ambiente de produção** (`load-tests/prod-like/`): a mesma bateria contra a pilha de `deploy/`, sem alterá-la,
  com PostgreSQL e nginx com TLS. Só os rate limits sobem, porque o k6 sai de um IP. Resultados:
  - ~400 ações/s dentro do SLO com login e compra;
  - gargalo de CPU (API e TLS); o PostgreSQL ficou folgado;
  - estoque íntegro depois de 3.484 pedidos.

  Também revelou problemas de produção, já corrigidos e verificados repetindo o mesmo teste (0 erros 5xx):
  - o rate limit por IP da borda (429 a partir de 30 req/s por IP), que passou para 100r/s;
  - a corrida no carrinho (500), agora com trava do usuário e 409 em violação de restrição;
  - um **deadlock** entre checkout e cancelamento, que só o PostgreSQL mostrou, agora com o estoque sempre travado
    em ordem de id do produto;
  - a falta de limite de memória da API (`mem_limit`).

  De quebra, os cabeçalhos de segurança duplicados entre a API e o nginx.

---

## ADR-024 — Bot de administração no Telegram

**Status:** Aceito

**Contexto**
O admin só acompanhava os pedidos abrindo o painel. Novos pedidos, Pix confirmados, cancelamentos e pedidos de
devolução passavam despercebidos, e tarefas rápidas (confirmar um Pix, marcar um envio, bloquear um usuário) exigiam
o computador. Já havia um bot do Telegram, mas só para os alertas técnicos do Grafana (ADR-022).

**Decisão**
- Um **segundo bot**, separado do bot dos alertas, que roda **dentro da API** (pacote `telegram`), desligado por
  padrão (`TELEGRAM_ADMIN_BOT_ENABLED`). O assistente `scripts/telegram-admin-bot-setup.sh` cria a configuração local.
- **Long polling** (`getUpdates`), sem webhook: a API só faz chamadas de saída para `api.telegram.org`, nada novo
  fica exposto na borda e funciona igual em dev, na rede local e em produção. Ao iniciar, o bot descarta mensagens
  acumuladas para não executar comandos antigos de surpresa.
- **Avisos de pedidos** por evento de domínio (`OrderEvent`: novo pedido, Pix confirmado pelo PSP, cancelamento pelo
  cliente, devolução, Pix expirado). Os serviços publicam o evento dentro da transação, e o bot só o recebe **depois
  do commit** (`@TransactionalEventListener`), numa thread própria: rollback não avisa, e o checkout não espera a rede
  do Telegram nem falha se ela cair.
- **Gestão pelo chat** com os **mesmos serviços do painel** (`AdminOrderService`, `AdminUserService`,
  `AdminDashboardService`): as regras de status, estoque, Pix e "não alterar a própria conta" não são duplicadas.
- **Quem pode usar:** `TELEGRAM_ADMIN_BOT_ADMINS` liga cada chat a uma conta (`chatId:email`).
  - Só conversa privada: em grupo, qualquer membro poderia clicar nos botões.
  - A cada mensagem, a conta precisa continuar ADMIN, ativa, não anonimizada e, com `REQUIRE_ADMIN_MFA`, com MFA.
    Bloquear o admin no painel corta o bot na hora.
  - O admin vai para o `SecurityContext` durante o comando, então a auditoria registra quem fez.
- **Toda ação que muda dados pede confirmação**: o botão `?…` só pergunta; o `!…` executa.

**Consequências**
- O admin recebe os pedidos no celular e resolve o dia a dia sem abrir o painel; o painel continua sendo o lugar das
  tarefas longas (produtos, auditoria, endereço completo).
- **A conta do Telegram passa a valer como acesso de admin.** Mitigações: lista fechada de chats, revalidação da
  conta a cada mensagem, confirmação, auditoria. Recomendação: verificação em duas etapas no Telegram de quem usa.
- **Dados pessoais vão para um terceiro (Telegram).** As mensagens levam o mínimo: nome, e-mail, total, itens e só
  cidade/UF do endereço. Tudo que vem do banco ou do cliente é escapado (HTML do Telegram).
- O token fica fora do código: `.env.telegram-admin-bot` (ignorado pelo Git, permissão 600) em dev e `.env.prod` em
  produção. Erros da API do Telegram trazem a URL com o token, e o cliente troca o token por `<token>` antes de
  logar. Configuração inválida impede o startup.
- Uma instância por token: duas APIs com o mesmo bot disputariam o `getUpdates` (o Telegram responde 409). Com
  várias réplicas, seria preciso um webhook ou eleger uma instância.
- Testes: `TelegramAdminBotTest` (autorização, comandos, confirmação, botões forjados, escape de HTML),
  `TelegramClientTest`, `TelegramBotPollerTest`, `TelegramOrderNotifierTest`, `TelegramBotPropertiesTest`,
  `TelegramBotIntegrationTest` (checkout real → aviso; cancelamento pelo bot → auditoria com o admin) e
  `scripts/test_telegram_admin_bot_setup.py` (assistente contra um Telegram falso).
