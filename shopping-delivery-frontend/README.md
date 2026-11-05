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
| Tailwind CSS          | 3        | Utilitários CSS, design responsivo               |
| lucide-react          | 1.x      | Ícones                                           |
| Vitest                | 5        | Testes unitários e de componentes                |
| Testing Library       | 16       | Renderização e interação nos testes (`@testing-library/react`, `user-event`, `jest-dom`) |
| @vitest/coverage-v8   | 5        | Relatório e mínimo de cobertura                  |

## Setup

Requer **Node.js 22.22+** (o CI usa Node 24).

```bash
npm install
npm run dev      # inicia em http://localhost:5173
npm run build    # build de produção
npx tsc -b       # verificação de tipos
npm run lint     # oxlint
npm test         # roda os testes uma vez
npm run test:watch
npm run coverage # testes + cobertura (falha abaixo do mínimo)
```

A API deve estar rodando em `http://localhost:8080`.

## Estrutura de arquivos

```
src/
├── api/
│   ├── axios.ts        # Instância base; interceptor injeta Bearer token;
│   │                   # interceptor de resposta redireciona 401/403 para /login
│   ├── auth.ts         # register(), login()
│   ├── products.ts     # getProducts(page, size, search?), getProduct(id)
│   ├── cart.ts         # getCart, addToCart, updateCartItem, removeCartItem, clearCart
│   ├── addresses.ts    # getAddresses, createAddress, deleteAddress
│   ├── orders.ts       # checkout, getOrders, getOrder
│   └── admin.ts        # dashboard, CRUD de produtos, pedidos (status), usuários (bloqueio/papel)
│
├── contexts/
│   ├── AuthContext.tsx  # user (com role), token, login(), register(), logout(),
│   │                    # isAuthenticated, isAdmin — persiste token e user em localStorage
│   └── CartContext.tsx  # cart, addItem(), updateItem(), removeItem(), clearCart()
│                        # Busca o carrinho do backend ao autenticar
│
├── components/
│   ├── Navbar.tsx        # Logo, link para carrinho com badge, login/logout
│   ├── ProtectedRoute.tsx # Redireciona para /login se não autenticado
│   ├── AdminRoute.tsx    # /login se visitante, / se não for ADMIN
│   ├── Pagination.tsx    # Paginação das tabelas de admin
│   └── ProductCard.tsx   # Card de produto com botão "Adicionar ao Carrinho"
│
├── pages/
│   ├── ProductListPage.tsx    # Grid de produtos, busca, paginação
│   ├── ProductDetailPage.tsx  # Detalhe do produto, seletor de quantidade
│   ├── LoginPage.tsx          # Formulário de login
│   ├── RegisterPage.tsx       # Formulário de cadastro
│   ├── CartPage.tsx           # Itens do carrinho, ajuste de quantidade, resumo
│   ├── OrdersPage.tsx         # Histórico de pedidos
│   ├── OrderDetailPage.tsx    # Detalhe de um pedido específico
│   ├── checkout/
│   │   └── CheckoutPage.tsx   # Wizard 3 etapas: endereço → revisão → confirmação
│   └── admin/
│       ├── AdminLayout.tsx        # Abas: Dashboard | Produtos | Pedidos | Usuários
│       ├── AdminDashboardPage.tsx # Indicadores e alerta de estoque baixo
│       ├── AdminProductsPage.tsx  # Tabela, busca, desativar/reativar
│       ├── ProductFormModal.tsx   # Criar/editar produto
│       ├── AdminOrdersPage.tsx    # Filtro por status, só transições válidas, detalhes
│       └── AdminUsersPage.tsx     # Busca, bloquear, promover, pedidos do cliente
│
├── utils/
│   └── format.ts  # formatBRL, formatDate, rótulos de status, transições, getErrorMessage()
│
├── test/          # setup do Vitest, helpers (renderWithProviders, loginAs) e fixtures
│
├── types/
│   └── index.ts   # Interfaces TypeScript espelhando os DTOs do backend
│
├── App.tsx        # Roteamento principal, composição de Providers
└── main.tsx       # Ponto de entrada React
```

## Fluxo de autenticação

```
Usuário faz login/register
  → AuthContext salva { token, user } no localStorage
  → axios.ts injeta "Authorization: Bearer <token>" em toda requisição
  → Se resposta for 401/403: remove localStorage e redireciona para /login
  → ProtectedRoute bloqueia rotas protegidas sem autenticação
  → AdminRoute libera /admin/** apenas quando user.role === 'ADMIN'
     (apenas UX — o back-end exige ROLE_ADMIN em /api/admin/**)
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

## Testes

```bash
npm run coverage
```

**104 testes** em 13 arquivos. Cobertura: **98,3% linhas · 93,3% branches · 94,6% funções**.

| Arquivo                               | O que cobre |
|---------------------------------------|-------------|
| `utils/format.test.ts`                | Formatação, tradução de erros, transições de status |
| `api/api.test.ts`                     | Interceptor de token, logout em 401/403, rota/método de cada função de API |
| `contexts/AuthContext.test.tsx`       | Login/cadastro/logout, `isAdmin`, sessão antiga sem `role`, JSON corrompido |
| `contexts/CartContext.test.tsx`       | Carrinho deslogado, carregamento e operações |
| `components/routes.test.tsx`          | `ProtectedRoute` e `AdminRoute` (visitante, cliente, admin) |
| `components/Navbar.test.tsx`          | Links por perfil, badge 9+, logout |
| `components/Pagination.test.tsx`      | Navegação e limites |
| `pages/auth.test.tsx`                 | Login (bloqueado, inválido, redirect) e cadastro (validação, email duplicado, API fora do ar) |
| `pages/shop.test.tsx`                 | Listagem, busca, paginação, card, detalhe com quantidade |
| `pages/cart-checkout.test.tsx`        | Carrinho e checkout completo, novo endereço, erros |
| `pages/orders.test.tsx`               | Histórico e detalhe de pedidos |
| `pages/admin/admin.test.tsx`          | Dashboard, produtos (CRUD/modal), pedidos (status), usuários (bloqueio/papel) |
| `App.test.tsx`                        | Rotas: home, `/admin` para admin, cliente barrado em `/admin` |

Padrão usado nos testes de página:

```tsx
vi.mock('../../api/admin');                       // nenhuma chamada HTTP real
vi.mocked(adminApi.getAdminUsers).mockResolvedValue(page([adminUser()]));
loginAs('ADMIN');                                 // sessão no localStorage
renderWithProviders(<AdminUsersPage />);          // AuthProvider + MemoryRouter
await userEvent.click(await screen.findByRole('button', { name: 'Bloquear' }));
```

## Fluxo do carrinho

```
CartContext.useEffect([isAuthenticated])
  → se autenticado: busca GET /api/cart do backend
  → se não autenticado: reseta para carrinho vazio local

addItem(productId, qty)
  → POST /api/cart/items → atualiza estado local com resposta da API
  (backend é a fonte de verdade; nunca calculamos totais no frontend)
```

## Checkout (3 etapas)

| Etapa | O que acontece                                                     |
|-------|--------------------------------------------------------------------|
| 1     | Lista endereços do usuário; permite criar novo inline              |
| 2     | Exibe itens do carrinho + endereço escolhido para revisão final   |
| 3     | Chama `POST /api/orders/checkout`; limpa CartContext; exibe #id   |

## Tipagem

Todos os tipos espelham exatamente os response records do backend:

```typescript
// src/types/index.ts
interface Product { id, name, description, imageUrl, price, stockQuantity }
interface CartSummary { items: CartItem[], total, itemCount }
interface Order { id, status: OrderStatus, total, createdAt, address, items }
type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED'
```

## Adicionando uma nova página

1. Crie o arquivo em `src/pages/NomeDaPagina.tsx`.
2. Se precisar de dados do backend, adicione a função em `src/api/`.
3. Adicione o tipo correspondente em `src/types/index.ts`.
4. Registre a rota em `src/App.tsx` (envolva com `<ProtectedRoute>` ou, para admin, adicione-a
   como filha de `/admin`, que já usa `<AdminRoute>`).
5. Crie o teste `NomeDaPagina.test.tsx` ao lado e rode `npm run coverage`.

## Variável de ambiente

Para apontar para uma API diferente em produção, crie `.env.production`:

```
VITE_API_BASE_URL=https://api.seudominio.com
```

E atualize `src/api/axios.ts`:
```typescript
baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
```
