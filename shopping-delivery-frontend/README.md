# ShopEasy — Frontend

Interface do e-commerce construída com React 19, TypeScript e Tailwind CSS: loja para clientes e
área administrativa (`/admin`) para usuários com papel `ADMIN`.

## Tecnologias

| Dependência           | Versão   | Uso                                              |
|-----------------------|----------|--------------------------------------------------|
| React                 | 19       | UI declarativa                                   |
| TypeScript            | 7        | Tipagem estática em todo o projeto               |
| Vite                  | 8        | Build tool e dev server com HMR                  |
| React Router DOM      | 7        | Roteamento client-side                           |
| Axios                 | 1.x      | HTTP client com interceptors de auth             |
| Tailwind CSS          | 3        | Utilitários CSS, design responsivo (mantido na v3: a v4 exige migração, ver ADR-020) |
| lucide-react          | 1.x      | Ícones                                           |
| qrcode                | 1.5      | Desenha o QR Code (SVG) a partir do Pix copia e cola do back-end |
| Vitest                | 5        | Testes unitários e de componentes                |
| Testing Library       | 16       | Renderização e interação nos testes (`@testing-library/react`, `user-event`, `jest-dom`) |
| @vitest/coverage-v8   | 5        | Relatório e mínimo de cobertura                  |

## Setup

Requer **Node.js 22.22+** (o CI usa Node 24).

```bash
npm ci           # instala exatamente o package-lock.json (rode de novo após cada git pull)
npm run dev      # inicia em http://localhost:5173 e no IP da rede (ex.: http://192.168.1.180:5173)
                 # antes, scripts/dev-preflight.mjs avisa se a branch não tem commits da main
npm run build    # build de produção
npx tsc -b       # verificação de tipos
npm run lint     # oxlint
npm test         # roda os testes uma vez
npm run test:watch
npm run coverage # testes + cobertura (falha abaixo do mínimo)
```

> O servidor serve **a branch em checkout**. Se uma funcionalidade "sumir", confira a branch antes de tudo e
> leia [`../shopping-delivery/docs/dev-workflow.md`](../shopping-delivery/docs/dev-workflow.md).

A API deve estar rodando na porta `8080` da mesma máquina. O front-end a encontra pelo host da página:
abrindo `http://localhost:5173` ele chama `http://localhost:8080`; abrindo `http://192.168.1.180:5173` (de outro
computador) chama `http://192.168.1.180:8080`. Detalhes em [Acessando de outros computadores](#acessando-de-outros-computadores-na-rede).

## Estrutura de arquivos

```
src/
├── api/
│   ├── baseUrl.ts      # resolveApiBaseUrl(): VITE_API_BASE_URL ou <host da página>:8080
│   ├── axios.ts        # Instância base; interceptor injeta Bearer token;
│   │                   # interceptor de resposta: 401 encerra a sessão e vai para /login (403 não)
│   ├── auth.ts         # register(acceptTerms), login(mfaCode?), logout() (revoga no servidor)
│   ├── account.ts      # perfil, exportação, exclusão de conta, MFA (setup/enable/disable)
│   ├── products.ts     # getProducts(page, size, search?), getProduct(id)
│   ├── cart.ts         # getCart, addToCart, updateCartItem, removeCartItem, clearCart
│   ├── addresses.ts    # getAddresses, createAddress, deleteAddress
│   ├── orders.ts       # checkout(addressId, 'PIX'), getOrders, getOrder, getPayment (polling)
│   └── admin.ts        # dashboard, CRUD de produtos, pedidos (status, confirmPayment), usuários
│
├── contexts/
│   ├── AuthContext.tsx  # user (com role), token, login(), register(), logout(),
│   │                    # isAuthenticated, isAdmin — persiste token e user em localStorage
│   └── CartContext.tsx  # cart, addItem(), updateItem(), removeItem(), clearCart()
│                        # Busca o carrinho do backend ao autenticar
│
├── components/
│   ├── Navbar.tsx        # Logo, carrinho com badge, "Minha conta" (também no mobile), login/logout
│   ├── FormField.tsx     # Input com label, dica e erro ligados por aria-describedby
│   ├── AddressForm.tsx   # Formulário de endereço validado (checkout e Minha conta)
│   ├── PixPayment.tsx    # QR Code (SVG), copia e cola, contador, polling do status
│   ├── ProtectedRoute.tsx # Redireciona para /login se não autenticado
│   ├── AdminRoute.tsx    # /login se visitante, / se não for ADMIN, /security se admin sem MFA obrigatório
│   ├── Footer.tsx        # Razão social, CNPJ, endereço, contato + links legais (Decreto 7.962/2013)
│   ├── QrCodeImage.tsx   # QR Code em SVG (MFA)
│   ├── Pagination.tsx    # Paginação das tabelas de admin
│   └── ProductCard.tsx   # Card de produto com botão "Adicionar ao Carrinho"
│
├── pages/
│   ├── ProductListPage.tsx    # Grid de produtos, busca, paginação
│   ├── ProductDetailPage.tsx  # Detalhe do produto, seletor de quantidade
│   ├── LoginPage.tsx          # Formulário de login
│   ├── RegisterPage.tsx       # Cadastro com validação por campo e máscara de telefone
│   ├── CartPage.tsx           # Itens do carrinho, ajuste de quantidade, resumo
│   ├── account/               # "Minha conta" (rotas protegidas)
│   │   ├── AccountLayout.tsx      # Abas: Meus pedidos | Histórico de compras | Endereços
│   │   ├── OrdersPage.tsx         # Resumo, filtro por status, busca, paginação
│   │   ├── OrderDetailPage.tsx    # Linha do tempo do status, endereço, itens com link
│   │   ├── PurchaseHistoryPage.tsx # Produtos já comprados (sem cancelados), ordenação
│   │   ├── AddressesPage.tsx      # Listar, cadastrar e excluir (com confirmação)
│   │   ├── PrivacyPage.tsx        # LGPD: corrigir dados, baixar JSON, excluir conta
│   │   └── SecurityPage.tsx       # MFA: QR Code, confirmar código, desativar
│   ├── legal/LegalPages.tsx   # Política de privacidade, termos de uso, trocas e devoluções (modelos)
│   ├── checkout/
│   │   └── CheckoutPage.tsx   # Wizard 3 etapas: endereço → revisão → confirmação
│   └── admin/
│       ├── AdminLayout.tsx        # Abas: Dashboard | Produtos | Pedidos | Usuários
│       ├── AdminDashboardPage.tsx # Indicadores e alerta de estoque baixo
│       ├── AdminProductsPage.tsx  # Tabela, busca, desativar/reativar
│       ├── ProductFormModal.tsx   # Criar/editar produto
│       ├── AdminOrdersPage.tsx    # Filtro, todos os status (permitidos via nextStatuses), confirmação ao cancelar
│       ├── AdminUsersPage.tsx     # Busca, bloquear, promover, pedidos do cliente
│       └── AdminAuditPage.tsx     # Trilha de auditoria com filtro por ação
│
├── hooks/
│   └── useValidatedForm.ts  # valores, erros visíveis após blur/envio, validateAll(), reset()
│
├── utils/
│   ├── format.ts      # formatBRL, formatDate, rótulos de pedido/pagamento, ORDER_STATUSES, getErrorMessage()
│   ├── clipboard.ts   # copyText(): Clipboard API ou fallback execCommand (http na rede local)
│   ├── validation.ts  # validateRegister, validateAddress, máscaras (CEP/telefone), UFS, LIMITS
│   └── orders.ts      # filtros, resumo, isSettled/isAwaitingPayment, agregado por produto, ordenação
│
├── test/          # setup do Vitest, helpers (renderWithProviders, loginAs) e fixtures
│
├── types/
│   └── index.ts   # Interfaces TypeScript espelhando os DTOs do backend
│
├── App.tsx        # Roteamento principal, composição de Providers
└── main.tsx       # Ponto de entrada React

scripts/
└── dev-preflight.mjs  # roda antes do `npm run dev` (predev): avisa se a branch está desatualizada
```

## Fluxo de autenticação

```
Usuário faz login/register
  → AuthContext salva { token, user } no localStorage
  → axios.ts injeta "Authorization: Bearer <token>" em toda requisição
  → Conta com MFA: o back-end responde "MFA code required" e o login mostra o campo do código
  → Se resposta for 401 (token expirado ou revogado): remove localStorage e redireciona para /login
  → 403 (sem permissão) mostra o erro na tela, sem derrubar a sessão
  → Sair chama POST /api/auth/logout: o back-end revoga este token e os de outros dispositivos
  → ProtectedRoute bloqueia rotas protegidas sem autenticação
  → AdminRoute libera /admin/** apenas quando user.role === 'ADMIN' e, se o MFA for obrigatório
     (mfaSetupRequired), manda antes para /security (apenas UX — o back-end exige ROLE_ADMIN e MFA)
```

## Mensagens de erro

`getErrorMessage(err)` em `utils/format.ts` é usado por todas as telas:

| Situação                         | Mensagem exibida |
|----------------------------------|------------------|
| Sem resposta (API fora do ar / CORS) | "Não foi possível conectar ao servidor…" |
| Erros de validação (`errors[]`)  | Lista de mensagens do back-end |
| Mensagens conhecidas             | Traduzidas (ex.: `Email already in use` → "Este email já está em uso.") |
| Demais                           | `message` do back-end ou o fallback da tela |

> Antes dessa função, o cadastro mostrava "email em uso" para **qualquer** erro, o que escondia um
> problema de CORS. Há um teste de regressão em `pages/auth.test.tsx`.
>
> Se essa mensagem aparecer só em ações de admin (status de pedido, bloquear usuário) e o back-end estiver
> no ar, verifique se o método (`PATCH`) está em `CorsConfig.ALLOWED_METHODS` — ver ADR-014.

## Status de pedido no admin

O seletor de cada pedido lista **todos** os status. O atual aparece marcado "(atual)" e os que o back-end
não permite ficam desabilitados — a lista de permitidos vem em `AdminOrder.nextStatuses`, então o front-end
não duplica a regra. Pedidos entregues/cancelados ficam com o seletor bloqueado ("Finalizado").
Cancelar pede confirmação, o seletor fica bloqueado enquanto salva e o resultado aparece na tela.

## Testes

```bash
npm run coverage
```

**279 testes** em 22 arquivos. Cobertura: **99,0% linhas · 95,8% branches · 96,7% funções**.
O CI roda, nesta ordem: `npm ci`, `tsc -b`, `lint`, `coverage` e `npm run build`. Os testes usam `css: false`,
então só o build valida Tailwind/PostCSS.

| Arquivo                               | O que cobre |
|---------------------------------------|-------------|
| `utils/format.test.ts`                | Formatação, tradução de erros (inclusive transição recusada), lista de status |
| `scripts/dev-preflight.test.mjs`      | Aviso do `npm run dev`: branch atualizada, sem commits da main, atrás do upstream, HEAD destacado |
| `utils/validation.test.ts`            | Cada regra de cadastro/endereço, máscaras, normalização do payload |
| `utils/orders.test.ts`                | Filtros, resumo e histórico só com pedidos pagos, agregado por `productId`, ordenação |
| `components/PixPayment.test.tsx`      | QR Code do copia e cola, valor, contador (fake timers), copiar / fallback manual, polling até pago sem consultas sobrepostas, resposta após desmontar ignorada, falha de rede ignorada, mensagens de expirado/cancelado/estorno |
| `utils/clipboard.test.ts`             | Clipboard API em contexto seguro, fallback em http, recusa de permissão, falha total |
| `components/AddressForm.test.tsx`     | Máscaras, erros no envio e no blur, acessibilidade, erro do back-end |
| `api/baseUrl.test.ts`                 | URL da API pelo host da página (localhost, IP, IPv6, https) e prioridade de `VITE_API_BASE_URL` |
| `api/api.test.ts`                     | Interceptor de token, logout só em 401 (403 e 400 mantêm a sessão), rota/método/corpo de cada função de API |
| `contexts/AuthContext.test.tsx`       | Login (com e sem MFA), cadastro com aceite, logout revoga (mesmo sem rede), `updateSession`/`updateName`/`clearSession`, sessão antiga, JSON corrompido |
| `contexts/CartContext.test.tsx`       | Carrinho deslogado, carregamento e operações |
| `components/routes.test.tsx`          | `ProtectedRoute` e `AdminRoute` (visitante, cliente, admin, admin sem MFA → /security) |
| `components/Navbar.test.tsx`          | Links por perfil, badge 9+, logout |
| `components/Pagination.test.tsx`      | Navegação e limites |
| `pages/auth.test.tsx`                 | Login (MFA, bloqueio por tentativas e rate limit, conta bloqueada, inválido, redirect) e cadastro (erros por campo, termos, blur, máscara, normalização, email duplicado, API fora do ar) |
| `pages/account/privacy-security.test.tsx` | Corrigir dados, baixar JSON, excluir conta (senha, confirmação, MFA), ativar/desativar MFA, aviso para admin |
| `pages/legal/legal.test.tsx`          | Conteúdo mínimo das páginas legais, aviso de modelo, rodapé com e sem `VITE_COMPANY_*` |
| `pages/shop.test.tsx`                 | Listagem, busca, paginação, card, detalhe com quantidade |
| `pages/cart-checkout.test.tsx`        | Carrinho; checkout endereço → revisão → Pix → pago → confirmação; pagar depois; Pix vencido; sem cobrança; erros |
| `pages/account/account.test.tsx`      | Abas, pedidos (resumo, filtros, busca, paginação, retry, selo Aguardando Pix), detalhe (linha do tempo, cancelado, Pix pendente → pago, selos de pagamento, cancelar pedido, arrependimento com prazo e motivo), histórico de compras, endereços |
| `pages/admin/admin.test.tsx`          | Dashboard, produtos (CRUD/modal), pedidos (todos os status, `nextStatuses`, pular etapas, confirmação ao cancelar, salvando, erro traduzido, falha de rede, Pix pendente/confirmar Pix, devolução solicitada e registrar estorno), usuários (bloqueio/papel), auditoria (filtro, paginação, vazio, erro) |
| `App.test.tsx`                        | Rotas: home, `/admin` para admin, cliente barrado em `/admin`, `/account` → `/orders`, visitante barrado em `/purchases` |

Padrão usado nos testes de página:

```tsx
vi.mock('../../api/admin');                       // nenhuma chamada HTTP real
vi.mocked(adminApi.getAdminUsers).mockResolvedValue(page([adminUser()]));
loginAs('ADMIN');                                 // sessão no localStorage
renderWithProviders(<AdminUsersPage />);          // AuthProvider + MemoryRouter
await userEvent.click(await screen.findByRole('button', { name: 'Bloquear' }));
```

## Validação de formulários

Cadastro e endereço usam `useValidatedForm` + funções puras de `utils/validation.ts`. O erro de um campo
aparece quando ele perde o foco ou no primeiro envio, e some assim que é corrigido. Formulários usam
`noValidate` (mensagens sempre em português) e cada input tem `aria-invalid` / `aria-describedby`.

| Formulário | Regras |
|------------|--------|
| Cadastro   | Nome e sobrenome (só letras, ≤ 100) · email válido (≤ 150) · senha 8–72 com letras e números (regra também no back-end) · aceite dos termos e da política · confirmação igual · telefone opcional com 10/11 dígitos, máscara `(11) 99999-9999` |
| Endereço   | CEP `00000-000` (máscara) · rua 3–150 · número `123`, `45A` ou `S/N` (≤ 10) · complemento opcional ≤ 100 · bairro 2–100 · cidade só letras 2–100 · UF entre as 27 válidas |

Os limites (`LIMITS`) são os mesmos das colunas do banco e do `@Size` no back-end (ADR-012).

## Minha conta

| Rota          | O que mostra |
|---------------|--------------|
| `/orders`     | Resumo (pedidos, total gasto sem cancelados, em andamento, entregues), filtros Todos / Em andamento / Entregues / Cancelados, busca por nº ou produto, 5 por página |
| `/orders/:id` | Linha do tempo `Pendente → Confirmado → Enviado → Entregue` (ou aviso de cancelado), endereço, itens com link para o produto |
| `/purchases`  | Produtos já comprados agrupados por `productId`: quantidade, total gasto, último preço, última compra; ordena por recência, quantidade ou gasto |
| `/addresses`  | Endereços de entrega: cadastrar (mesmo `AddressForm` do checkout) e excluir com confirmação; endereço usado em pedido não pode ser excluído |

| `/privacy`    | **LGPD**: corrigir nome/telefone, **baixar meus dados** (JSON), **excluir conta** (senha + código MFA, se ativo) |
| `/security`   | Verificação em duas etapas (TOTP): QR Code para o app autenticador, confirmar código, desativar |

No detalhe do pedido: **Cancelar pedido** enquanto não foi enviado e **Solicitar devolução** até 7 dias após a
entrega (direito de arrependimento, CDC art. 49), com o prazo exibido.

`/account` redireciona para `/orders`. Pedidos, resumo e histórico são calculados a partir de `GET /api/orders` (ADR-013).

## Páginas legais e dados da empresa

`/privacidade`, `/termos` e `/trocas-e-devolucoes` são **modelos** escritos a partir do que o sistema faz (dados
coletados, bases legais, retenção, direitos, Pix, arrependimento). O rodapé mostra razão social, CNPJ, endereço e
contato. Tudo vem de `VITE_COMPANY_*` (ver `.env.example`); enquanto faltar algum dado, as páginas mostram
`[preencher]` e um aviso. **Revise os textos com um advogado antes de publicar.**

## Fluxo do carrinho

```
CartContext.useEffect([isAuthenticated])
  → se autenticado: busca GET /api/cart do backend
  → se não autenticado: reseta para carrinho vazio local

addItem(productId, qty)
  → POST /api/cart/items → atualiza estado local com resposta da API
  (backend é a fonte de verdade; nunca calculamos totais no frontend)
```

## Checkout (4 etapas)

| Etapa | O que acontece |
|-------|----------------|
| 1 Endereço    | Lista endereços do usuário; permite criar novo inline (`AddressForm` validado) |
| 2 Revisão     | Itens, endereço e forma de pagamento (Pix); "Confirmar e pagar com Pix" chama `POST /api/orders/checkout` |
| 3 Pagamento   | `PixPayment`: QR Code, valor, contador, "Copiar código Pix"; consulta `GET /api/orders/{id}/payment` a cada 5 s. "Pagar depois" leva ao detalhe do pedido |
| 4 Confirmação | Avança sozinho quando o Pix é pago. Se vencer, a tela avisa que o pedido foi cancelado |

### Pix na tela

- O QR Code é gerado no navegador a partir do **copia e cola do back-end** (`qrcode`, saída SVG) — o front-end
  nunca monta o BR Code nem calcula o valor.
- O polling encadeia `setTimeout` (não `setInterval`): a próxima consulta só sai depois da resposta anterior,
  então rede lenta não gera requisições sobrepostas nem aviso duplicado de "pago".
- O contador usa `expiresInSeconds` calculado no servidor, então não depende do relógio/fuso do computador.
- **Copiar** usa a Clipboard API em https/localhost; acessando por `http://<ip>` na rede, cai no fallback
  `execCommand`. Se nada funcionar, o código fica selecionado para Ctrl+C.
- O Pix pendente também aparece em **Minha conta → pedido**; a lista mostra o selo "Aguardando Pix".
- Total gasto e histórico de compras só contam pedidos pagos.

## Tipagem

Todos os tipos espelham exatamente os response records do backend:

```typescript
// src/types/index.ts
interface Product { id, name, description, imageUrl, price, stockQuantity }
interface CartSummary { items: CartItem[], total, itemCount }
interface OrderItem { id, productId, productName, quantity, unitPrice, subtotal }
interface Order { id, status: OrderStatus, total, createdAt, address, items, payment? }
interface Payment { method: 'PIX', status: PaymentStatus, amount, txid, pixCopyPaste, expiresAt, paidAt, expiresInSeconds }
type PaymentStatus = 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELLED' | 'REFUND_PENDING'
type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED'
```

## Adicionando uma nova página

1. Crie o arquivo em `src/pages/NomeDaPagina.tsx`.
2. Se precisar de dados do backend, adicione a função em `src/api/`.
3. Adicione o tipo correspondente em `src/types/index.ts`.
4. Registre a rota em `src/App.tsx` (envolva com `<ProtectedRoute>` ou, para admin, adicione-a
   como filha de `/admin`, que já usa `<AdminRoute>`).
5. Crie o teste `NomeDaPagina.test.tsx` ao lado e rode `npm run coverage` e `npm run build`.

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

> ⚠️ Em rede compartilhada, lembre que o ambiente de dev expõe o console H2 (`/h2-console`) e o admin
> padrão `admin@shopeasy.com` / `admin123`. Defina `ADMIN_EMAIL`/`ADMIN_PASSWORD` se outras pessoas usarem a rede.

## Variáveis de ambiente

Copie `.env.example` para `.env.local` (dev) ou passe como `--build-arg` no `Dockerfile` (produção).

| Variável | Uso |
|----------|-----|
| `VITE_API_BASE_URL` | `/` = mesma origem (produção atrás do nginx, recomendado) · vazio = `<host da página>:8080` (dev) · URL = API em outro domínio |
| `VITE_COMPANY_*` | Identificação do fornecedor e email do encarregado (LGPD) |
| `VITE_TERMS_VERSION` | Igual a `TERMS_VERSION` do back-end |

`resolveApiBaseUrl()` em `src/api/baseUrl.ts` aplica essas regras. Nada de segredo em `VITE_*`: vai para o bundle.

## Produção

`Dockerfile` faz o build e serve com `nginx-unprivileged`. A configuração do nginx (TLS, HSTS, CSP, rate limit,
proxy de `/api`) está em [`../shopping-delivery/deploy/`](../shopping-delivery/deploy/) — ver ADR-019.
