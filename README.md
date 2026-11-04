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
| [Shopping Delivery](#2-shopping-delivery) | API de e-commerce com gestão de pedidos e estoque | Flyway · Bean Validation · H2 |
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

> `shopping-delivery/shopping-delivery/`

API REST de e-commerce com ciclo completo de pedidos: clientes, endereços, catálogo de itens, pedidos e itens de pedido. Inclui validação de estoque em tempo real e cálculo automático de totais.

**Destaques técnicos:**
- Migrações de banco de dados versionadas com Flyway (7 migrations)
- Validação de estoque: decrementa ao adicionar item, restaura ao remover
- Ciclo de vida de pedido: `PENDENTE → APROVADO → ENTREGUE` (ou `CANCELADO`)
- Exceções de negócio tipadas (`EstoqueInsuficienteException`, `CpfExistenteException`, etc.)
- Cobertura de testes com 10+ classes de testes (controllers, services, repository)

**Stack:** Java 17 · Spring Boot 3.3.7 · JPA · Flyway · Bean Validation · H2 · OpenAPI

```bash
cd shopping-delivery/shopping-delivery
./mvnw spring-boot:run
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

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
├── shopping-delivery/            # E-commerce (Flyway + testes completos)
├── spring-ai/                    # Chat + imagens com OpenAI
├── voice-transcription/          # Transcrição de áudio (Whisper)
├── text-to-speec/                # Síntese de voz (TTS)
├── web-api/                      # Web MVC com Thymeleaf
└── web-security/                 # Auth com Spring Security
```

Cada projeto é **independente** e possui seu próprio `pom.xml`, podendo ser executado separadamente.

---

## Autor

**SSKings** · [github.com/SSKings](https://github.com/SSKings)
