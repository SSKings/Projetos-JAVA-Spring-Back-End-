# ShopEasy — E-commerce Simples

Projeto full-stack de e-commerce com API REST em Spring Boot e interface em React + TypeScript,
com loja para clientes e **área administrativa** para gerenciar produtos, pedidos e usuários.

## Estrutura do repositório

```
Projetos-JAVA-Spring-Back-End-/
├── shopping-delivery/
│   ├── shopping-delivery/        ← Backend (Spring Boot)
│   │   ├── src/
│   │   ├── Dockerfile            ← imagem da API (sem root)
│   │   └── pom.xml
│   ├── deploy/                   ← produção: docker-compose, nginx (TLS/CSP), .env.prod.example
│   ├── scripts/                  ← simulate-pix-payment.sh (dev)
│   └── docs/
│       ├── api-reference.md      ← Referência completa dos endpoints
│       ├── architecture.md       ← Modelo de domínio e decisões técnicas
│       ├── adr.md                ← Architecture Decision Records
│       └── security-review.md    ← Revisão de segurança, status das correções, checklist legal
└── shopping-delivery-frontend/   ← Frontend (React + TS), na raiz do repositório
    ├── src/
    └── package.json
```

## Stack

| Camada       | Tecnologia                                          |
|--------------|-----------------------------------------------------|
| API          | Spring Boot 3.5.16, Java 17, Spring Security + JWT, MFA TOTP |
| Persistência | Spring Data JPA, H2 (dev), PostgreSQL (prod), Flyway |
| Frontend     | React 19, TypeScript, Tailwind CSS, React Router v7 |
| HTTP client  | Axios (com interceptors de auth e 401 redirect)     |
| Testes BE    | JUnit 5, Mockito, `@WebMvcTest`, JaCoCo             |
| Testes FE    | Vitest, Testing Library, `@vitest/coverage-v8`      |

## Pré-requisitos

- Java 17+
- Maven 3.9+
- Node.js 22.22+ (recomendado 24 — exigido pelo Vitest/jsdom)

## Rodando localmente

```bash
# 1. Backend (porta 8080)
cd shopping-delivery/shopping-delivery
mvn spring-boot:run

# 2. Frontend (porta 5173) — a partir da raiz do repositório
cd shopping-delivery-frontend
npm install
npm run dev
```

Acesse `http://localhost:5173` — a API estará em `http://localhost:8080`.

O perfil padrão é **`dev`** (H2 em memória, Swagger e console H2 ligados, segredos de exemplo).

**Login de administrador (dev):** `admin@shopeasy.com` / `admin123` → link **Admin** na barra superior.
Em produção o admin vem de `ADMIN_EMAIL`/`ADMIN_PASSWORD` e precisa ativar a verificação em duas etapas
(**Minha conta → Segurança**) antes de usar a área administrativa.

> Console H2 disponível em `http://localhost:8080/h2-console`
> Swagger UI em `http://localhost:8080/swagger-ui/index.html`

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

## Pagamento via Pix

O checkout gera um **Pix "copia e cola" e QR Code** no padrão BR Code do Banco Central, com o valor do pedido,
a chave da loja e um identificador (`txid`) único. O cliente paga no app do banco e a tela atualiza sozinha.

| Variável                 | Padrão (dev)                         | O que é |
|--------------------------|--------------------------------------|---------|
| `PIX_KEY`                | `pix@shopeasy.example`               | Chave Pix da loja (email, CPF/CNPJ só dígitos, `+55…` ou aleatória). O padrão usa o domínio reservado `.example`: não existe no DICT, então nenhum teste paga para terceiros. **Defina a chave real para receber.** |
| `PIX_MERCHANT_NAME`      | `ShopEasy`                           | Nome do recebedor no app do banco (até 25 caracteres) |
| `PIX_MERCHANT_CITY`      | `Sao Paulo`                          | Cidade do recebedor (até 15) |
| `PIX_EXPIRATION_MINUTES` | `30`                                 | Validade do QR Code; vencido = pedido cancelado e estoque devolvido |
| `PIX_WEBHOOK_SECRET`     | `dev-pix-webhook-secret-change-me`   | Segredo HMAC do webhook do PSP. **Troque em produção**; vazio desativa o webhook |

**Como o pagamento é confirmado.** Um QR Code com chave Pix não avisa a loja sozinho. Há dois caminhos:

1. **Webhook de um PSP** (banco/gateway com API Pix): ele chama `POST /api/payments/pix/webhook` assinado com
   HMAC-SHA256 (`X-Pix-Timestamp` + `X-Pix-Signature`). O back-end confere a assinatura, a janela de 5 minutos e
   o valor exato antes de marcar como pago. Contrato em [`docs/api-reference.md`](docs/api-reference.md#pagamentos-pix).
2. **Manual**: o admin vê o Pix no extrato (mesmo valor e identificador) e clica em **Confirmar Pix**.

Para **simular** o PSP em desenvolvimento:

```bash
# txid e valor aparecem na tela de pagamento ("Identificador") e no admin
./scripts/simulate-pix-payment.sh <txid> 59.80
```

> Para receber de verdade, defina `PIX_KEY` com a chave real da loja. Sem PSP, use a confirmação manual.

## Produção

```bash
cd shopping-delivery/deploy
cp .env.prod.example .env.prod          # preencha (openssl rand ... para os segredos)
mkdir certs && cp /caminho/fullchain.pem /caminho/privkey.pem certs/
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

Sobe PostgreSQL (sem porta exposta), API em perfil `prod` (container read-only, sem root) e nginx com TLS,
HSTS, CSP, rate limit e proxy de `/api` na mesma origem. A API **não inicia** com segredos ausentes, fracos ou
de desenvolvimento, CORS aberto ou banco H2 (`ProductionConfigValidator`). Detalhes: ADR-017 a ADR-019 e
[`docs/security-review.md`](docs/security-review.md) (status de cada correção e o que ainda depende da empresa:
CNPJ e textos legais revisados, PSP para Pix dinâmico, NF-e, email transacional).

## Fluxo principal

```
Usuário acessa produtos (público)
  → Cria conta (validada no front e no back, aceite dos termos) / faz login (MFA opcional) → recebe JWT
  → Adiciona produtos ao carrinho
  → Checkout: escolhe endereço → revisa (forma de pagamento: Pix) → paga → confirmação
  → Pedido criado (PENDING), estoque reservado, carrinho limpo, Pix gerado (QR Code + copia e cola)
  → Pix pago (webhook do PSP ou admin) → pedido CONFIRMED · Pix vencido → pedido CANCELLED, estoque devolvido
  → Minha conta (/orders): pedidos com filtro/busca e linha do tempo do status,
    histórico de compras por produto (/purchases) e endereços de entrega (/addresses)

  → Pode cancelar o pedido até o envio e pedir devolução até 7 dias após a entrega
  → Dados e privacidade: corrige, baixa (JSON) e exclui (anonimiza) os próprios dados

Administrador (/admin, MFA obrigatório em produção)
  → Dashboard: faturamento, pedidos pendentes, clientes, estoque baixo
  → Produtos: cria, edita, desativa/reativa
  → Pedidos: filtra por status e avança PENDING → CONFIRMED → SHIPPED → DELIVERED,
             podendo pular etapas (ou cancela antes do envio — o estoque é devolvido);
             pedido com Pix pendente só pode ser cancelado; "Confirmar Pix" após conferir o extrato
  → Usuários: busca, bloqueia/desbloqueia, promove a admin, vê pedidos do cliente
  → Auditoria: quem fez o quê, quando e de qual IP
```

## Testes e cobertura

| Projeto   | Comando                 | Testes | Linhas | Branches |
|-----------|-------------------------|--------|--------|----------|
| Backend   | `./mvnw verify`         | 221    | 97,7%  | 93,4%    |
| Frontend  | `npm run coverage`      | 276    | 99,0%  | 95,8%    |

Os dois comandos **falham se a cobertura cair** abaixo do mínimo (backend 85% linhas / 80% branches;
frontend 90% linhas / 85% branches) e rodam no CI a cada push.
Relatórios HTML: `shopping-delivery/target/site/jacoco/index.html` e `shopping-delivery-frontend/coverage/index.html`.

## Documentação

- [Referência da API](docs/api-reference.md)
- [Arquitetura e modelo de domínio](docs/architecture.md)
- [Decisões de arquitetura (ADR)](docs/adr.md)
- [Revisão de segurança e prontidão para produção](docs/security-review.md) — vulnerabilidades encontradas, checklist técnico e legal (LGPD, CDC, Marco Civil, Pix)
