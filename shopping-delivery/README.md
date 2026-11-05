# ShopEasy — E-commerce Simples

Projeto full-stack de e-commerce com API REST em Spring Boot e interface em React + TypeScript,
com loja para clientes e **área administrativa** para gerenciar produtos, pedidos e usuários.

## Estrutura do repositório

```
Projetos-JAVA-Spring-Back-End-/
├── shopping-delivery/
│   ├── shopping-delivery/        ← Backend (Spring Boot)
│   │   ├── src/
│   │   └── pom.xml
│   └── docs/
│       ├── api-reference.md      ← Referência completa dos endpoints
│       ├── architecture.md       ← Modelo de domínio e decisões técnicas
│       └── adr.md                ← Architecture Decision Records
└── shopping-delivery-frontend/   ← Frontend (React + TS), na raiz do repositório
    ├── src/
    └── package.json
```

## Stack

| Camada       | Tecnologia                                          |
|--------------|-----------------------------------------------------|
| API          | Spring Boot 3.3.7, Java 17, Spring Security + JWT   |
| Persistência | Spring Data JPA, H2 (dev), Flyway                   |
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

**Login de administrador (dev):** `admin@shopeasy.com` / `admin123` → link **Admin** na barra superior.
Em produção, defina `ADMIN_EMAIL` e `ADMIN_PASSWORD`.

> Console H2 disponível em `http://localhost:8080/h2-console`
> Swagger UI em `http://localhost:8080/swagger-ui/index.html`

## Fluxo principal

```
Usuário acessa produtos (público)
  → Cria conta / faz login  → recebe JWT
  → Adiciona produtos ao carrinho
  → Checkout: escolhe endereço → revisa → confirma
  → Pedido criado, estoque deduzido, carrinho limpo
  → Consulta histórico de pedidos

Administrador (/admin)
  → Dashboard: faturamento, pedidos pendentes, clientes, estoque baixo
  → Produtos: cria, edita, desativa/reativa
  → Pedidos: filtra por status e avança PENDING → CONFIRMED → SHIPPED → DELIVERED
             (ou cancela — o estoque é devolvido)
  → Usuários: busca, bloqueia/desbloqueia, promove a admin, vê pedidos do cliente
```

## Testes e cobertura

| Projeto   | Comando                 | Testes | Linhas | Branches |
|-----------|-------------------------|--------|--------|----------|
| Backend   | `./mvnw verify`         | 106    | 94,8%  | 87,5%    |
| Frontend  | `npm run coverage`      | 104    | 98,3%  | 93,3%    |

Os dois comandos **falham se a cobertura cair** abaixo do mínimo (backend 85% linhas / 80% branches;
frontend 90% linhas / 85% branches) e rodam no CI a cada push.
Relatórios HTML: `shopping-delivery/target/site/jacoco/index.html` e `shopping-delivery-frontend/coverage/index.html`.

## Documentação

- [Referência da API](docs/api-reference.md)
- [Arquitetura e modelo de domínio](docs/architecture.md)
- [Decisões de arquitetura (ADR)](docs/adr.md)
