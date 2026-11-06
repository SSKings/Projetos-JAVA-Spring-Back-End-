# ShopEasy — Referência da API

Base URL: `http://localhost:8080`

Rotas protegidas exigem o cabeçalho:
```
Authorization: Bearer <token>
```

| Acesso   | Rotas                                   | Sem permissão |
|----------|-----------------------------------------|---------------|
| Público  | `POST /api/auth/login`, `POST /api/auth/register`, `GET /api/products/**`, `POST /api/payments/pix/webhook` (assinatura HMAC) | — |
| Cliente  | `/api/cart/**`, `/api/addresses/**`, `/api/orders/**`, `/api/account/**`, `POST /api/auth/logout` | **401** sem token válido |
| Admin    | `/api/admin/**` (exige `ROLE_ADMIN`; em produção, também MFA ativo) | **403** para quem não é admin |
| Actuator | `GET /actuator/health` público (só `{"status":"UP"}`); detalhes, `/actuator/metrics/**` e `/actuator/prometheus` só ADMIN; demais endpoints → 404 (ADR-022) | **401** / **403** |

**401 × 403.** 401 = sem login, token expirado ou **revogado**: o front-end encerra a sessão. 403 = logado sem
permissão: a sessão continua. Corpo: `{"status":401,"message":"Authentication required"}` / `{"status":403,"message":"Access denied"}`.

**Limites.**
- Login e cadastro: 20 requisições/min por IP (`429 Too many requests` + `Retry-After`).
- Após 5 senhas erradas, o email fica bloqueado por 15 min (`429 Too many failed attempts. Try again later`).
  Vale **igualmente para emails não cadastrados**, para que a resposta não revele quem tem conta.
- A reautenticação (`/api/account/delete`, `/api/account/mfa/disable`) conta para esse mesmo bloqueio.
- Webhook: 120/min por IP.
- Corpo da requisição: no máximo 64 KB (`security.max-request-bytes`) → acima disso, `413 Request body too large`.
- Conflito de edição simultânea (lock otimista) → `409`.

> O navegador envia um *preflight* `OPTIONS` antes de requisições com JSON. O CORS precisa estar
> habilitado **também na cadeia do Spring Security** (`.cors(Customizer.withDefaults())`), senão o
> preflight recebe 403 e o navegador bloqueia a chamada.
>
> `CorsConfig.ALLOWED_METHODS` precisa listar **todos** os verbos da API: `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
> Sem `PATCH`, alterar status de pedido e bloquear/promover usuário no admin falhava no navegador com
> "Não foi possível conectar ao servidor" (o axios não recebe resposta). Coberto por `CorsPreflightTest`.
>
> Cabeçalhos aceitos: `Authorization`, `Content-Type`, `X-Pix-Timestamp`, `X-Pix-Signature`, `traceparent`,
> `tracestate`. Os dois últimos (W3C Trace Context) vêm da instrumentação OpenTelemetry do front (ADR-022).

---

## Autenticação

### POST /api/auth/register

Cria uma nova conta de usuário.

**Request**
```json
{
  "name": "João Silva",
  "email": "joao@example.com",
  "password": "senha1234",
  "phone": "(11) 99999-9999",
  "acceptTerms": true
}
```

| Campo      | Regras no back-end (Bean Validation)        | Regras extras no front-end |
|------------|---------------------------------------------|----------------------------|
| `name`     | obrigatório, até 100 caracteres             | só letras, nome e sobrenome |
| `email`    | obrigatório, formato de email, até 150      | exige domínio com ponto (`nome@dominio.com`) |
| `password` | obrigatório, 8 a 72 caracteres, letras **e** números | campo "Confirmar senha" |
| `acceptTerms` | obrigatório e `true` (versão e data do aceite ficam gravadas) | checkbox com links para termos e política |
| `phone`    | opcional, até 20 caracteres                 | 10 ou 11 dígitos, máscara `(11) 99999-9999` |

Os limites espelham as colunas da tabela `users`; sem eles, um valor longo demais gerava erro 500 no banco.

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
| 400    | Campos inválidos (`errors[]`, ex.: `Password must be between 6 and 72 characters`) ou `Email already in use` |

---

### POST /api/auth/login

Autentica o usuário e retorna um JWT (validade `JWT_EXPIRATION_MINUTES`, padrão 120). O token carrega a versão
da sessão do usuário (`ver`): logout, bloqueio, troca de papel e alterações de MFA invalidam tokens antigos.

**Request** — `mfaCode` só para quem ativou a verificação em duas etapas:
```json
{
  "email": "joao@example.com",
  "password": "senha1234",
  "mfaCode": "123456"
}
```

**Response 200**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "name": "João Silva",
  "email": "joao@example.com",
  "role": "CUSTOMER",
  "mfaEnabled": false,
  "mfaSetupRequired": false
}
```

O front-end usa `role` para exibir (ou não) a área administrativa. `mfaSetupRequired = true` (admin sem MFA com
`REQUIRE_ADMIN_MFA`) → `/api/admin/**` responde 403 até o MFA ser ativado.

**Erros**
| Código | Motivo |
|--------|--------|
| 400    | `Invalid credentials` — email ou senha incorretos (mesma resposta e mesmo tempo para email inexistente) |
| 400    | `Account is blocked` — conta bloqueada por um admin |
| 400    | `MFA code required` — senha correta, falta o código · `Invalid MFA code` — errado ou já usado |
| 429    | `Too many failed attempts. Try again later` (`Retry-After`), também para email não cadastrado · `Too many requests` |

### POST /api/auth/logout

Revoga **todos** os tokens do usuário (este e os de outros dispositivos). **204**.

---

## Minha conta (LGPD)

| Método | Rota | Descrição |
|--------|------|-----------|
| GET    | `/api/account` | Perfil: nome, email, telefone, papel, MFA, versão e data do aceite dos termos |
| PUT    | `/api/account` | Correção (art. 18, III): `{ "name": "...", "phone": "..." }` |
| GET    | `/api/account/data-export` | Acesso/portabilidade (art. 18, II e V): JSON com perfil, endereços, pedidos e eventos de segurança (`Content-Disposition: attachment`) |
| POST   | `/api/account/delete` | Eliminação (art. 18, VI): `{ "password": "...", "mfaCode": "..." }` → **204**. Anonimiza a conta; pedidos ficam sem vínculo pessoal. `400 Account has orders in progress` / `Invalid password` / `Invalid MFA code` · `429` após 5 falhas (mesmo bloqueio do login) |
| POST   | `/api/account/mfa/setup` | Gera o segredo TOTP: `{ "secret": "BASE32", "otpauthUri": "otpauth://totp/ShopEasy:..." }` (QR Code) |
| POST   | `/api/account/mfa/enable` | `{ "code": "123456" }` → ativa, revoga sessões antigas e devolve um **novo** `AuthResponse` |
| POST   | `/api/account/mfa/disable` | `{ "password": "...", "mfaCode": "123456" }` → devolve novo `AuthResponse` |

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

| Campo          | Back-end                    | Front-end (`utils/validation.ts`) |
|----------------|-----------------------------|-----------------------------------|
| `zipCode`      | obrigatório, até 10         | formato `00000-000` (máscara automática) |
| `street`       | obrigatório, até 150        | 3 a 150 caracteres |
| `number`       | opcional, até 10            | obrigatório: número (`123`, `45A`) ou `S/N` |
| `complement`   | opcional, até 100           | opcional, até 100 |
| `neighborhood` | obrigatório, até 100        | 2 a 100 caracteres |
| `city`         | obrigatório, até 100        | só letras, 2 a 100 |
| `state`        | obrigatório, 2 caracteres   | uma das 27 UFs, convertida para maiúsculas |

Campo inválido → **400** com `errors[]` (ex.: `Street must have at most 150 characters`).

**Response 201** — retorna o endereço criado (mesmo formato do GET)

---

### DELETE /api/addresses/{id}

Remove um endereço do usuário.

**Response 204** — sem corpo

**Erros**
| Código | Motivo |
|--------|--------|
| 400    | `Address is linked to an order` — o endereço já foi usado em um pedido (`orders.address_id` é FK) |
| 404    | Endereço não encontrado ou pertence a outro usuário |

---

## Pedidos

### POST /api/orders/checkout

Finaliza a compra. Em uma única transação: valida estoque → decrementa estoque → grava pedido com snapshot de preços → **cria a cobrança Pix** → limpa carrinho.

**Request**
```json
{
  "addressId": 10,
  "paymentMethod": "PIX"
}
```

`paymentMethod` é opcional (padrão e único valor hoje: `PIX`). O valor cobrado é sempre o `total` calculado no
servidor — o cliente não envia valor.

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
      "productId": 1,
      "productName": "Notebook Pro 15",
      "quantity": 2,
      "unitPrice": 4599.90,
      "subtotal": 9199.80
    }
  ],
  "payment": {
    "method": "PIX",
    "status": "PENDING",
    "amount": 9199.80,
    "txid": "K3J9QZ0A7M2X5B8C1D4E6F9GH",
    "pixCopyPaste": "00020101021226...6304A1B2",
    "createdAt": "2024-06-15T14:32:00",
    "expiresAt": "2024-06-15T15:02:00",
    "paidAt": null,
    "expiresInSeconds": 1800
  }
}
```

`pixCopyPaste` é o BR Code ("Pix copia e cola"); o QR Code é a representação gráfica dele. Só é enviado
enquanto `status = PENDING`. `payment` é `null` em pedidos anteriores à V4.

**Erros**
| Código | Motivo |
|--------|--------|
| 400    | Carrinho vazio |
| 400    | Estoque insuficiente para algum item |
| 404    | Endereço não encontrado |

---

### GET /api/orders

Lista todos os pedidos do usuário autenticado, em ordem decrescente de data.

O front-end usa esta rota para toda a área **Minha conta**: resumo, filtros e busca em `/orders` e o
histórico de compras por produto em `/purchases` (agrupado por `items[].productId`, sem pedidos cancelados).

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

**Response 200** — mesmo formato do item acima, mais:

```json
{ "deliveredAt": "2026-03-15T12:00:00", "returnRequestedAt": null, "returnDeadline": "2026-03-22T12:00:00",
  "canCancel": false, "canRequestReturn": true }
```

### POST /api/orders/{id}/cancel

Cancelamento pelo cliente enquanto o pedido não foi enviado (`PENDING`/`CONFIRMED`): devolve o estoque; Pix
pendente é cancelado; Pix pago vira `REFUND_PENDING`. `400 Order can no longer be cancelled` depois do envio.

### POST /api/orders/{id}/return

Direito de arrependimento (CDC art. 49): até **7 dias** depois de `deliveredAt`. Corpo opcional
`{ "reason": "..." }` (até 500 caracteres). `400 Return period has expired or a return was already requested`.

**Erros**
| Código | Motivo |
|--------|--------|
| 404    | Pedido não encontrado ou pertence a outro usuário |

---

## Pagamentos (Pix)

### GET /api/orders/{id}/payment

Status do Pix de um pedido do usuário autenticado (a tela de pagamento consulta a cada 5 s). Se o prazo já
passou, a própria consulta expira a cobrança, cancela o pedido e devolve o estoque.

**Response 200** — o objeto `payment` mostrado no checkout.

| Código | Motivo |
|--------|--------|
| 404    | `Payment not found` — pedido sem pagamento ou de outro usuário |

### POST /api/payments/pix/webhook

Notificação de Pix recebido, enviada pelo **PSP** (banco/gateway). Rota pública: a autenticação é a assinatura.

**Headers**
| Header            | Valor |
|-------------------|-------|
| `X-Pix-Timestamp` | epoch em segundos; aceito até 5 min de diferença (anti-replay) |
| `X-Pix-Signature` | `hex(HMAC-SHA256(PIX_WEBHOOK_SECRET, "<timestamp>.<corpo bruto>"))` |

**Request**
```json
{ "txid": "K3J9QZ0A7M2X5B8C1D4E6F9GH", "endToEndId": "E00000000202406151435abcdefghijk", "amount": 9199.80 }
```

**Response 200** — `{ "status": "PAID" }` (ou `REFUND_PENDING`). É idempotente: reenviar o mesmo evento
devolve 200 sem alterar nada.

| Código | Motivo |
|--------|--------|
| 400    | `Paid amount does not match the charge` (valor diferente do cobrado) ou `Invalid webhook payload` |
| 401    | `Invalid webhook signature`, `Missing webhook signature`, `Webhook timestamp outside the allowed window`, `Pix webhook is not configured` |
| 403    | `Source IP not allowed` — origem fora de `PIX_WEBHOOK_ALLOWED_IPS` (IPs/CIDRs do PSP) |

Toda rejeição fica registrada na auditoria (`WEBHOOK_REJECTED`), mesmo com rollback da transação.
| 404    | `Payment not found` (txid desconhecido) |

Simulação em dev: `shopping-delivery/scripts/simulate-pix-payment.sh '<pix copia e cola>'` (txid e valor lidos do
BR Code) ou `<txid> <valor>`; `--dry-run` mostra o corpo sem enviar.

### Status do pagamento

| Valor            | Significado | Efeito no pedido |
|------------------|-------------|------------------|
| `PENDING`        | QR Code válido, aguardando | Pedido `PENDING`; admin só pode cancelar |
| `PAID`           | Confirmado (webhook ou admin) | Pedido vai para `CONFIRMED` |
| `EXPIRED`        | Prazo (`PIX_EXPIRATION_MINUTES`) acabou | Pedido `CANCELLED`, estoque devolvido |
| `CANCELLED`      | Admin cancelou antes do pagamento | — |
| `REFUND_PENDING` | Dinheiro recebido com pedido cancelado (ou cancelado depois de pago) | Loja precisa devolver o Pix |
| `REFUNDED`       | Admin registrou a devolução do Pix | — |

---

## Administração (ROLE_ADMIN)

Todas as rotas abaixo exigem `Authorization: Bearer <token>` de um usuário com papel `ADMIN`.
Clientes comuns recebem **403**. O login/registro agora também retornam `role` (`CUSTOMER` | `ADMIN`).

### GET /api/admin/dashboard

```json
{ "totalProducts": 10, "activeProducts": 9, "lowStockProducts": 2, "totalCustomers": 35,
  "totalOrders": 120, "pendingOrders": 4, "revenue": 15890.50 }
```

`revenue` soma pedidos **pagos** e não cancelados (pedidos antigos, sem pagamento, contam como pagos).

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
| POST   | `/api/admin/orders/{id}/payment/confirm` | Confirma o Pix manualmente (após conferir o extrato); pedido → `CONFIRMED`. 400 `Payment is not pending` |

Pedido com Pix **não pago** só pode ir para `CANCELLED` (`nextStatuses = ["CANCELLED"]`); outra transição
retorna 400 `Order payment is pending`. Cancelar com Pix pendente cancela a cobrança; com Pix pago, marca
`REFUND_PENDING`.

O pedido só anda para frente e **pode pular etapas**; cancelar só é possível antes do envio:

| De          | Para                                         |
|-------------|----------------------------------------------|
| `PENDING`   | `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED` |
| `CONFIRMED` | `SHIPPED`, `DELIVERED`, `CANCELLED`          |
| `SHIPPED`   | `DELIVERED`                                  |
| `DELIVERED`, `CANCELLED` | — (finais)                      |

Outras retornam 400 `Cannot change order status from SHIPPED to CANCELLED`. Cancelar devolve os itens ao estoque.

Toda resposta de pedido do admin traz `nextStatuses` — as transições permitidas a partir do status atual
(`OrderStatus.nextStatuses()`). O front-end usa esse campo em vez de duplicar a regra:

```json
{ "id": 5, "status": "CONFIRMED", "customerName": "Ana", "...": "...",
  "nextStatuses": ["SHIPPED", "DELIVERED", "CANCELLED"] }
```

Estorno: `POST /api/admin/orders/{id}/payment/refund` — registra que o Pix foi devolvido (pagamento
`REFUND_PENDING`, ou `PAID` com devolução solicitada) → `REFUNDED`. `400 Payment cannot be refunded`.
Marcar `DELIVERED` grava `deliveredAt` (início do prazo de arrependimento). A resposta inclui `returnRequestedAt`
e `returnReason`.

### Auditoria

`GET /api/admin/audit?action=&page=&size=` — trilha somente leitura: `createdAt`, `actorId`, `actorEmail`,
`action`, `targetType`, `targetId`, `details`, `ip`. Ações: `LOGIN_SUCCESS`, `LOGIN_FAILED`, `ACCOUNT_LOCKED`,
`LOGOUT`, `REGISTER`, `MFA_ENABLED`, `MFA_DISABLED`, `ORDER_STATUS_CHANGED`, `ORDER_CANCELLED_BY_CUSTOMER`,
`RETURN_REQUESTED`, `PAYMENT_CONFIRMED`, `PAYMENT_EXPIRED`, `PAYMENT_REFUND_PENDING`, `PAYMENT_REFUNDED`,
`WEBHOOK_REJECTED`, `USER_UPDATED`, `PRODUCT_CHANGED`, `PROFILE_UPDATED`, `DATA_EXPORTED`, `ACCOUNT_DELETED`.

### Usuários

| Método | Rota | Descrição |
|--------|------|-----------|
| GET    | `/api/admin/users?search=&page=&size=` | Lista (busca por nome ou email) |
| GET    | `/api/admin/users/{id}` | Detalhe (inclui `orderCount`) |
| GET    | `/api/admin/users/{id}/orders` | Pedidos do usuário |
| PATCH  | `/api/admin/users/{id}` | Body `{ "active": false }` e/ou `{ "role": "ADMIN" }` |

O admin não pode alterar a própria conta (400). Usuário bloqueado recebe 400 `Account is blocked` no login.
Bloquear ou trocar o papel **revoga as sessões abertas** do usuário (tokens antigos → 401).

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

Erros de requisição têm código próprio e **não** geram stack trace no log:

| Situação | Código | `message` |
|----------|--------|-----------|
| Rota inexistente (com login) | 404 | `Resource not found` |
| Método HTTP não suportado | 405 (+ cabeçalho `Allow`) | `Method not allowed` |
| `Content-Type` não suportado | 415 | `Unsupported media type` |
| Parâmetro ou cabeçalho obrigatório ausente | 400 | `Missing required parameter` |
| `?sort=` com campo inexistente | 400 | `Invalid sort property` |
| Corpo acima de 64 KB | 413 | `Request body too large` |
| JSON malformado ou tipo errado | 400 | `Malformed request or invalid value` |
| Erro inesperado | 500 | `Internal server error` (detalhe só no log do servidor) |

---

## Status de pedido

| Valor       | Descrição                        |
|-------------|----------------------------------|
| `PENDING`   | Pedido recebido, aguardando processamento |
| `CONFIRMED` | Pedido confirmado pelo vendedor  |
| `SHIPPED`   | Pedido despachado                |
| `DELIVERED` | Pedido entregue ao cliente       |
| `CANCELLED` | Pedido cancelado                 |
