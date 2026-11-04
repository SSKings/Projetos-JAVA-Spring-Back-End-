# API Gestor Financeiro

<p>
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?style=flat-square&logo=springboot&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring%20Security-JWT-6DB33F?style=flat-square&logo=springsecurity&logoColor=white"/>
  <img src="https://img.shields.io/badge/PostgreSQL-Production-4169E1?style=flat-square&logo=postgresql&logoColor=white"/>
  <img src="https://img.shields.io/badge/Docker-Ready-2496ED?style=flat-square&logo=docker&logoColor=white"/>
</p>

API REST para gerenciamento de finanças pessoais. Permite registro de usuários com autenticação JWT, controle de contas bancárias, cartões de crédito e lançamentos financeiros (receitas e despesas), com suporte a roles e logout seguro.

## Funcionalidades

- **Autenticação JWT**: registro, login e logout com token blacklist (tokens invalidados no logout)
- **Controle de acesso**: roles `ADMIN` e `USER` via Spring Security
- **Gestão de Contas**: CRUD de contas bancárias por usuário
- **Gestão de Cartões**: CRUD de cartões de crédito por usuário
- **Lançamentos**: registro de receitas e despesas com filtragem por tipo e fonte
- **Tipos e Fontes**: cadastro de tipos e fontes de lançamento customizados

## Stack

| Tecnologia | Uso |
|-----------|-----|
| Java 17 + Spring Boot 3.2.5 | Core da aplicação |
| Spring Security + JWT (Auth0) | Autenticação e autorização |
| Spring Data JPA + Hibernate | Acesso ao banco de dados |
| H2 Database | Banco em memória (desenvolvimento/teste) |
| PostgreSQL | Banco relacional (produção) |
| SpringDoc OpenAPI 2.5.0 | Documentação Swagger UI |
| Docker + Docker Compose | Containerização |
| Lombok | Redução de boilerplate |

## Como executar

### Com Docker (recomendado)

```bash
git clone https://github.com/SSKings/Projetos-JAVA-Spring-Back-End-.git
cd Projetos-JAVA-Spring-Back-End-/api.gestor.financeiro

# Cria e sobe a API + PostgreSQL
docker-compose up --build
```

A API estará disponível em `http://localhost:8080`.

### Local (modo desenvolvimento)

```bash
cd api.gestor.financeiro
./mvnw spring-boot:run -Dspring.profiles.active=development
```

Usa banco H2 em memória — nenhuma configuração extra necessária.

### Variáveis de ambiente

| Variável | Descrição | Obrigatória |
|----------|-----------|-------------|
| `JWT_SECRET` | Chave secreta para assinar tokens JWT (mín. 32 chars) | Produção |
| `DB_URL` | URL do PostgreSQL (ex: `jdbc:postgresql://localhost:5432/gestor`) | Produção |
| `DB_USERNAME` | Usuário do banco | Produção |
| `DB_PASSWORD` | Senha do banco | Produção |

## Endpoints principais

### Autenticação

| Método | Endpoint | Descrição | Auth? |
|--------|----------|-----------|-------|
| `POST` | `/auth/register` | Registro de novo usuário | Não |
| `POST` | `/auth/login` | Login — retorna JWT | Não |
| `POST` | `/auth/logout` | Logout — invalida o token | Sim |

### Recursos protegidos (requer `Authorization: Bearer <token>`)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET/POST` | `/contas` | Listar / criar contas |
| `GET/PUT/DELETE` | `/contas/{id}` | Buscar / atualizar / remover conta |
| `GET/POST` | `/cartoes` | Listar / criar cartões |
| `GET/PUT/DELETE` | `/cartoes/{id}` | Buscar / atualizar / remover cartão |
| `GET/POST` | `/lancamentos` | Listar / registrar lançamentos |
| `GET/PUT/DELETE` | `/lancamentos/{id}` | Buscar / atualizar / remover lançamento |
| `GET/POST` | `/tipos-lancamento` | Tipos de lançamento |
| `GET/POST` | `/fontes-lancamento` | Fontes de lançamento |

Documentação completa e interativa: **`http://localhost:8080/swagger-ui.html`**

## Arquitetura

```
src/main/java/com/sskings/api/gestor/financeiro/
├── controllers/        # Endpoints REST
├── services/           # Regras de negócio
├── repositories/       # Acesso ao banco (Spring Data JPA)
├── models/             # Entidades JPA
├── dto/                # DTOs organizados por domínio
│   ├── usuario/
│   ├── conta/
│   ├── cartao/
│   └── lancamento/
├── security/           # JWT filter, SecurityConfig, TokenBlackList
├── exception/          # Exceções de negócio customizadas
├── errors/             # Handler global de erros (ExceptionHandler)
└── documentation/      # Configuração do Swagger
```

## Perfis de execução

| Perfil | Banco | Ativação |
|--------|-------|----------|
| `development` | H2 (console em `/h2-console`) | `-Dspring.profiles.active=development` |
| `production` | PostgreSQL | variáveis de ambiente + Docker |
| `test` | H2 (isolado) | automático nos testes |

## Testes

```bash
./mvnw test
```

Testes implementados:
- `UsuarioServiceTest` — 14 casos de teste com Mockito (BDD style)
- `UsuarioRepositoryTest` — testes de repositório com H2
- `SecurityConfigTest` — validação das configurações de segurança
