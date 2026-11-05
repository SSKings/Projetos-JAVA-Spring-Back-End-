# ShopEasy — Referência da API

Base URL: `http://localhost:8080`

Rotas protegidas exigem o cabeçalho:
```
Authorization: Bearer <token>
```

| Acesso   | Rotas                                   | Sem permissão |
|----------|-----------------------------------------|---------------|
| Público  | `POST /api/auth/**`, `GET /api/products/**` | — |
| Cliente  | `/api/cart/**`, `/api/addresses/**`, `/api/orders/**` | 403 |
| Admin    | `/api/admin/**` (exige `ROLE_ADMIN`)    | 403 |

> O navegador envia um *preflight* `OPTIONS` antes de requisições com JSON. O CORS precisa estar
> habilitado **também na cadeia do Spring Security** (`.cors(Customizer.withDefaults())`), senão o
> preflight recebe 403 e o navegador bloqueia a chamada.

---

## Autenticação

### POST /api/auth/register

Cria uma nova conta de usuário.

**Request**
```json
{
  "name": "João Silva",
  "email": "joao@example.com",
  "password": "senha123"
}
```

**Response 201**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "name": "João Silva",
  "email": "joao@example.com",
  "role": "CUSTOMER"
}
```

Todo cadastro público cria um `CUSTOMER`. Contas `ADMIN` são criadas pelo `AdminSeeder` ou promovidas por outro admin.

**Erros**
| Código | Motivo |
|--------|--------|
| 400    | Campos inválidos (`errors[]`) ou `Email already in use` |

---

### POST /api/auth/login

Autentica o usuário e retorna um JWT com validade de 2 horas.

**Request**
```json
{
  "email": "joao@example.com",
  "password": "senha123"
}
```

**Response 200**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "name": "João Silva",
  "email": "joao@example.com",
  "role": "CUSTOMER"
}
```

O front-end usa `role` para exibir (ou não) a área administrativa.

**Erros**
| Código | Motivo |
|--------|--------|
| 400    | `Invalid credentials` — email ou senha incorretos |
| 400    | `Account is blocked` — conta bloqueada por um admin |

---

## Produtos

### GET /api/products

Lista produtos ativos com paginação e busca opcional. Rota pública.

**Query params**
| Param    | Tipo    | Padrão | Descrição                     |
|----------|---------|--------|-------------------------------|
| `page`   | integer | 0      | Número da página (zero-based) |
| `size`   | integer | 12     | Itens por página              |
| `search` | string  | —      | Filtra por nome (case-insensitive, parcial) |

**Exemplo**
```
GET /api/products?page=0&size=12&search=notebook
```

**Response 200**
```json
{
  "content": [
    {
      "id": 1,
      "name": "Notebook Pro 15",
      "description": "Intel Core i7, 16 GB RAM, SSD 512 GB",
      "imageUrl": "https://images.unsplash.com/...",
      "price": 4599.90,
      "stockQuantity": 15
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 12,
  "first": true,
  "last": true
}
```

---

### GET /api/products/{id}

Retorna um produto pelo ID. Rota pública.

**Response 200**
```json
{
  "id": 1,
  "name": "Notebook Pro 15",
  "description": "Intel Core i7, 16 GB RAM, SSD 512 GB",
  "imageUrl": "https://images.unsplash.com/...",
  "price": 4599.90,
  "stockQuantity": 15
}
```

**Erros**
| Código | Motivo |
|--------|--------|
| 404    | Produto não encontrado |

---

## Carrinho

Todas as rotas de carrinho exigem JWT. O carrinho é vinculado ao usuário autenticado.

### GET /api/cart

Retorna o carrinho do usuário autenticado.

**Response 200**
```json
{
  "items": [
    {
      "id": 42,
      "productId": 1,
      "productName": "Notebook Pro 15",
      "productImageUrl": "https://images.unsplash.com/...",
      "unitPrice": 4599.90,
      "quantity": 2,
      "subtotal": 9199.80
    }
  ],
  "total": 9199.80,
  "itemCount": 2
}
```

---

### POST /api/cart/items

Adiciona um produto ao carrinho. Se o produto já existir, incrementa a quantidade.

**Request**
```json
{
  "productId": 1,
  "quantity": 2
}
```

**Response 200** — retorna o carrinho atualizado (mesmo formato do GET /api/cart)

**Erros**
| Código | Motivo |
|--------|--------|
| 404    | Produto não encontrado |
| 400    | Estoque insuficiente |

---

### PUT /api/cart/items/{id}

Atualiza a quantidade de um item do carrinho.

**Path param:** `id` — ID do CartItem (não do produto)

**Request**
```json
{
  "quantity": 3
}
```

**Response 200** — retorna o carrinho atualizado

**Erros**
| Código | Motivo |
|--------|--------|
| 404    | Item não encontrado ou pertence a outro usuário |
| 400    | Estoque insuficiente |

---

### DELETE /api/cart/items/{id}

Remove um item específico do carrinho.

**Path param:** `id` — ID do CartItem

**Response 200** — retorna o carrinho atualizado

**Erros**
| Código | Motivo |
|--------|--------|
| 404    | Item não encontrado ou pertence a outro usuário |

---

### DELETE /api/cart

Limpa todos os itens do carrinho do usuário.

**Response 204** — sem corpo

---

## Endereços

### GET /api/addresses

Lista todos os endereços do usuário autenticado.

**Response 200**
```json
[
  {
    "id": 10,
    "street": "Rua das Flores",
    "number": "123",
    "complement": "Apto 4",
    "neighborhood": "Centro",
    "city": "São Paulo",
    "state": "SP",
    "zipCode": "01001-000"
  }
]
```

---

### POST /api/addresses

Cria um novo endereço para o usuário autenticado.

**Request**
```json
{
  "street": "Rua das Flores",
  "number": "123",
  "complement": "Apto 4",
  "neighborhood": "Centro",
  "city": "São Paulo",
  "state": "SP",
  "zipCode": "01001-000"
}
```

Todos os campos exceto `complement` são obrigatórios.

**Response 201** — retorna o endereço criado (mesmo formato do GET)

---

### DELETE /api/addresses/{id}

Remove um endereço do usuário.

**Response 204** — sem corpo

**Erros**
| Código | Motivo |
|--------|--------|
| 404    | Endereço não encontrado ou pertence a outro usuário |

---

## Pedidos

### POST /api/orders/checkout

Finaliza a compra. Em uma única transação: valida estoque → decrementa estoque → grava pedido com snapshot de preços → limpa carrinho.

**Request**
```json
{
  "addressId": 10
}
```

**Response 201**
```json
{
  "id": 55,
  "status": "PENDING",
  "total": 9199.80,
  "createdAt": "2024-06-15T14:32:00",
  "address": {
    "id": 10,
    "street": "Rua das Flores",
    "number": "123",
    "complement": "Apto 4",
    "neighborhood": "Centro",
    "city": "São Paulo",
    "state": "SP",
    "zipCode": "01001-000"
  },
  "items": [
    {
      "id": 1,
      "productName": "Notebook Pro 15",
      "quantity": 2,
      "unitPrice": 4599.90,
      "subtotal": 9199.80
    }
  ]
}
```

**Erros**
| Código | Motivo |
|--------|--------|
| 400    | Carrinho vazio |
| 400    | Estoque insuficiente para algum item |
| 404    | Endereço não encontrado |

---

### GET /api/orders

Lista todos os pedidos do usuário autenticado, em ordem decrescente de data.

**Response 200**
```json
[
  {
    "id": 55,
    "status": "PENDING",
    "total": 9199.80,
    "createdAt": "2024-06-15T14:32:00",
    "address": { ... },
    "items": [ ... ]
  }
]
```

---

### GET /api/orders/{id}

Retorna um pedido específico do usuário.

**Response 200** — mesmo formato do item acima

**Erros**
| Código | Motivo |
|--------|--------|
| 404    | Pedido não encontrado ou pertence a outro usuário |

---

## Administração (ROLE_ADMIN)

Todas as rotas abaixo exigem `Authorization: Bearer <token>` de um usuário com papel `ADMIN`.
Clientes comuns recebem **403**. O login/registro agora também retornam `role` (`CUSTOMER` | `ADMIN`).

### GET /api/admin/dashboard

```json
{ "totalProducts": 10, "activeProducts": 9, "lowStockProducts": 2, "totalCustomers": 35,
  "totalOrders": 120, "pendingOrders": 4, "revenue": 15890.50 }
```

### Produtos

| Método | Rota | Descrição |
|--------|------|-----------|
| GET    | `/api/admin/products?search=&page=&size=` | Lista todos (inclusive inativos) |
| GET    | `/api/admin/products/{id}` | Detalhe |
| POST   | `/api/admin/products` | Cria (201) |
| PUT    | `/api/admin/products/{id}` | Atualiza |
| DELETE | `/api/admin/products/{id}` | Desativa — exclusão lógica (204) |

```json
{ "name": "Caneca", "description": "Cerâmica 300ml", "imageUrl": "https://...",
  "price": 29.90, "stockQuantity": 10, "active": true }
```

### Pedidos

| Método | Rota | Descrição |
|--------|------|-----------|
| GET    | `/api/admin/orders?status=&page=&size=` | Lista todos os pedidos (com cliente) |
| GET    | `/api/admin/orders/{id}` | Detalhe |
| PATCH  | `/api/admin/orders/{id}/status` | Body `{ "status": "SHIPPED" }` |

Transições permitidas: `PENDING → CONFIRMED | CANCELLED`, `CONFIRMED → SHIPPED | CANCELLED`,
`SHIPPED → DELIVERED`. Outras retornam 400. Cancelar devolve os itens ao estoque.

### Usuários

| Método | Rota | Descrição |
|--------|------|-----------|
| GET    | `/api/admin/users?search=&page=&size=` | Lista (busca por nome ou email) |
| GET    | `/api/admin/users/{id}` | Detalhe (inclui `orderCount`) |
| GET    | `/api/admin/users/{id}/orders` | Pedidos do usuário |
| PATCH  | `/api/admin/users/{id}` | Body `{ "active": false }` e/ou `{ "role": "ADMIN" }` |

O admin não pode alterar a própria conta (400). Usuário bloqueado recebe 400 `Account is blocked` no login.

---

## Formato de erro padrão

Todos os erros retornam o seguinte payload:

```json
{
  "status": 400,
  "message": "Estoque insuficiente para o produto: Notebook Pro 15",
  "timestamp": "2024-06-15T14:32:00"
}
```

---

## Status de pedido

| Valor       | Descrição                        |
|-------------|----------------------------------|
| `PENDING`   | Pedido recebido, aguardando processamento |
| `CONFIRMED` | Pedido confirmado pelo vendedor  |
| `SHIPPED`   | Pedido despachado                |
| `DELIVERED` | Pedido entregue ao cliente       |
| `CANCELLED` | Pedido cancelado                 |
