# Java Spring Boot — Back-End Portfolio

<p align="center">
  <img src="https://img.shields.io/badge/Java-17%20%7C%2021-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.x%20%7C%204.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white"/>
  <img src="https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white"/>
  <img src="https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white"/>
  <img src="https://github.com/SSKings/Projetos-JAVA-Spring-Back-End-/actions/workflows/ci.yml/badge.svg" alt="CI Status"/>
</p>

<p align="center">
  Coleção de projetos back-end construídos com Java e Spring Boot, cobrindo REST APIs, segurança com JWT, integrações com IA (OpenAI) e aplicações web com Thymeleaf.
</p>

---

## Projetos

| Projeto | Descrição | Stack principal |
|---------|-----------|-----------------|
| [API Gestor Financeiro](#1-api-gestor-financeiro) | API REST completa de finanças pessoais com JWT | Spring Security · JWT · PostgreSQL · Docker |
| [Shopping Delivery](#2-shopping-delivery) | E-commerce full-stack com área administrativa | Spring Security · JWT · React · Flyway |
| [Spring AI](#3-spring-ai) | Chat, geração de imagens e receitas via OpenAI | Spring AI · OpenAI |
| [Voice Transcription](#4-voice-transcription) | Transcrição de áudio para texto (Whisper) | Spring AI · OpenAI Whisper |
| [Text to Speech](#5-text-to-speech) | Síntese de voz a partir de texto | Spring AI · OpenAI TTS |
| [Web API](#6-web-api) | Aplicação web MVC com Thymeleaf e Bootstrap | Thymeleaf · MySQL · Bootstrap |
| [Web Security](#7-web-security) | Demonstração de autenticação com Spring Security | Spring Security · BCrypt · MySQL |

---

## 1. API Gestor Financeiro

> `api.gestor.financeiro/`

API REST para gerenciamento de finanças pessoais. Permite registro de usuários com autenticação JWT, controle de contas, cartões e lançamentos (receitas/despesas), com roles de acesso e logout com token blacklist.

**Destaques técnicos:**
- Autenticação stateless com JWT (Auth0 library) + token blacklist para logout seguro
- Roles de acesso (`ADMIN` / `USER`) via Spring Security
- DTOs organizados por domínio, exceções customizadas com HTTP status corretos
- Documentação interativa via Swagger UI (`/swagger-ui.html`)
- Suporte a múltiplos perfis: H2 (dev), PostgreSQL (prod), H2 (test)
- Containerização com Docker + Docker Compose

**Stack:** Java 17 · Spring Boot 3.2.5 · Spring Security · JWT · JPA · H2 · PostgreSQL · OpenAPI · Docker

```bash
cd api.gestor.financeiro
docker-compose up          # sobe a API + PostgreSQL
# ou
./mvnw spring-boot:run -Dspring.profiles.active=development
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## 2. Shopping Delivery

> `shopping-delivery/shopping-delivery/` (API) · `shopping-delivery-frontend/` (React)

E-commerce full-stack (**ShopEasy**): catálogo, carrinho, checkout em 3 etapas e área "Minha conta" (pedidos,
histórico de compras e endereços) para clientes, e uma **área administrativa** para gerenciar produtos, pedidos e usuários.

**Destaques técnicos:**
- Autenticação stateless com JWT e controle de acesso por papel (`CUSTOMER` / `ADMIN`) em `/api/admin/**`
- Bloqueio de usuários com efeito imediato (login recusado e token atual invalidado)
- Checkout atômico (`@Transactional`): valida e baixa estoque, grava snapshot de preço, limpa carrinho
- Pagamento via **Pix** (BR Code/EMV do Bacen gerado no servidor, QR Code + copia e cola), webhook com HMAC-SHA256 e anti-replay, expiração automática que devolve o estoque
- Máquina de estados do pedido `PENDING → CONFIRMED → SHIPPED → DELIVERED` (pode pular etapas); cancelar devolve o estoque
- Exclusão lógica de produtos, preservando o histórico de pedidos
- Migrações versionadas com Flyway e admin inicial criado no startup
- Validação de cadastro e endereço no front-end (máscaras, mensagens por campo, acessível) espelhando o Bean Validation
- Segurança revisada e corrigida: segredos fora do código com validação no startup, estoque atômico (sem venda acima do estoque), rate limit e bloqueio por tentativas, revogação de sessão, MFA (TOTP) para admin, auditoria, CSP/HSTS — ver [`security-review.md`](shopping-delivery/docs/security-review.md)
- LGPD e CDC no produto: exportar/corrigir/excluir dados, aceite de termos, cancelamento e direito de arrependimento
- Produção com Docker: PostgreSQL, API sem root e nginx com TLS ([`shopping-delivery/deploy/`](shopping-delivery/deploy/))
- Observabilidade com **OpenTelemetry**: um clique no front vira um trace até o SQL no Grafana (Tempo), com logs (Loki) e métricas (Prometheus) correlacionados, dashboards prontos de saúde da API e de negócio/segurança e 12 alertas com notificação no **Telegram** ([`observability-stack/`](shopping-delivery/observability-stack/README.md))
- **554 testes** com cobertura exigida no build: backend 97,7% de linhas (JaCoCo) e frontend 99,0% (Vitest). O CI também roda:
  - o build de produção do front;
  - a verificação de CVEs (OSV + `npm audit`);
  - 39 testes dos scripts (Python e shell);
  - a validação dos dashboards e alertas;
  - um **teste funcional k6 contra a API rodando**.

  O Dependabot é agrupado.
- **Capacidade medida com k6:** ~400 ações/s dentro do SLO na **pilha de produção** (PostgreSQL + nginx com TLS, com login e compra) e ~1.600/s em dev. Estoque íntegro sob saturação, e duas corridas e um deadlock achados pelo teste já corrigidos ([`load-tests/`](shopping-delivery/load-tests/README.md))
- Documentação de API, arquitetura e ADRs em [`shopping-delivery/docs/`](shopping-delivery/docs/)

**Stack:** Java 17 · Spring Boot 3.5.16 · Spring Security · JWT · TOTP · JPA · Flyway · H2 · PostgreSQL · OpenAPI · Docker · nginx ·
React 19 · TypeScript · Tailwind · Vitest · OpenTelemetry · Grafana (Tempo, Loki, Prometheus) · k6

```bash
cd shopping-delivery/shopping-delivery && ./mvnw spring-boot:run   # API em :8080
cd shopping-delivery-frontend && npm install && npm run dev         # UI em :5173
```

Admin (dev): `admin@shopeasy.com` com a senha mostrada no log da API (ou a de `ADMIN_PASSWORD`) · Swagger UI: `http://localhost:8080/swagger-ui/index.html`

---

## 3. Spring AI

> `spring-ai/`

Demonstração da integração Spring AI com OpenAI: chat com GPT, geração de imagens com DALL-E e geração de receitas com PromptTemplate.

**Endpoints:**
- `GET /ask-ai?message=...` — Chat simples com GPT
- `GET /ask-ai-options?message=...` — Chat com parâmetros customizáveis (temperatura, modelo)
- `GET /recipe-creator` — Geração de receitas com ingredientes e restrições alimentares
- `GET /generate-image` — Geração de imagens via DALL-E

**Stack:** Java 21 · Spring Boot 3.4.3 · Spring AI 1.0.0 · OpenAI API

```bash
export OPEN_AI_API_KEY=sk-...
cd spring-ai && ./mvnw spring-boot:run
```

---

## 4. Voice Transcription

> `voice-transcription/`

Serviço de transcrição de áudio para texto usando o modelo Whisper da OpenAI via Spring AI.

**Endpoint:** `POST /ai/transcribe` — recebe arquivo de áudio (multipart) e retorna a transcrição em texto.

**Stack:** Java 21 · Spring Boot 3.4.5 · Spring AI 1.0.0-RC1 · OpenAI Whisper

```bash
export OPEN_AI_API_KEY=sk-...
cd voice-transcription && ./mvnw spring-boot:run
```

---

## 5. Text to Speech

> `text-to-speec/`

Serviço de síntese de voz: converte texto em áudio MP3 usando a API de TTS da OpenAI.

**Endpoint:** `POST /api/tts/synthesize` — recebe JSON com texto e retorna áudio `audio/mpeg` para download.

**Stack:** Java 17 · Spring Boot 4.0.2 · Spring AI 2.0.0-M2 · OpenAI TTS

```bash
export OPEN_AI_API_KEY=sk-...
cd text-to-speec && ./mvnw spring-boot:run
```

---

## 6. Web API

> `web-api/`

Aplicação web MVC com renderização server-side (Thymeleaf) para gerenciamento de clientes, itens e pedidos. Interface construída com Bootstrap 5.

**Stack:** Java 17 · Spring Boot 3.2.0 · Thymeleaf · Bootstrap 5.3.2 · jQuery · MySQL

```bash
# Configure MySQL em src/main/resources/application.properties
cd web-api && ./mvnw spring-boot:run
```

---

## 7. Web Security

> `web-security/`

Demonstração de autenticação e autorização com Spring Security: registro de usuários, login com formulário, criptografia de senha (BCrypt) e controle de rotas protegidas.

**Destaques técnicos:**
- `UserDetailsService` com persistência em banco (MySQL)
- Senha criptografada com BCrypt via bean injetado
- Integração com Thymeleaf Security Extras para controle de visibilidade na UI

**Stack:** Java 17 · Spring Boot 3.2.0 · Spring Security · BCrypt · Thymeleaf · MySQL

```bash
# Configure MySQL em src/main/resources/application.properties
cd web-security && ./mvnw spring-boot:run
```

---

## Pré-requisitos

| Ferramenta | Versão mínima |
|------------|---------------|
| JDK | 17 (projetos Java 17) ou 21 (projetos AI) |
| Maven | 3.6+ (ou use o Maven Wrapper incluso: `./mvnw`) |
| Docker | 20+ (apenas para `api.gestor.financeiro`) |
| Node.js | 22.22+ (apenas para `shopping-delivery-frontend`; CI usa 24) |
| MySQL | 8+ (para `web-api` e `web-security`) |
| OpenAI API Key | — (para projetos `spring-ai`, `voice-transcription`, `text-to-speec`) |

---

## Estrutura do repositório

```
Projetos-JAVA-Spring-Back-End-/
├── .github/
│   └── workflows/
│       └── ci.yml               # CI: build e testes automáticos
├── api.gestor.financeiro/        # Finanças pessoais (JWT + Docker)
├── shopping-delivery/            # E-commerce: API + docs (JWT, admin, Flyway, JaCoCo)
├── shopping-delivery-frontend/   # E-commerce: SPA React + TypeScript (Vitest)
├── spring-ai/                    # Chat + imagens com OpenAI
├── voice-transcription/          # Transcrição de áudio (Whisper)
├── text-to-speec/                # Síntese de voz (TTS)
├── web-api/                      # Web MVC com Thymeleaf
└── web-security/                 # Auth com Spring Security
```

Cada projeto é **independente** e possui seu próprio `pom.xml` (ou `package.json`, no caso do frontend), podendo ser executado separadamente.

---

## Autor

**SSKings** · [github.com/SSKings](https://github.com/SSKings)
