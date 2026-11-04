# Spring AI — Integração com OpenAI

<p>
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4.3-6DB33F?style=flat-square&logo=springboot&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring%20AI-1.0.0-6DB33F?style=flat-square&logo=spring&logoColor=white"/>
  <img src="https://img.shields.io/badge/OpenAI-GPT%20%7C%20DALL--E-412991?style=flat-square&logo=openai&logoColor=white"/>
</p>

Demonstração de integração com a API da OpenAI usando o framework Spring AI. Cobre chat completion com GPT, geração de imagens com DALL-E e geração de receitas com PromptTemplate.

## Funcionalidades

- **Chat simples** — envia uma mensagem e recebe resposta do GPT
- **Chat com opções** — controle de temperatura, modelo e outros parâmetros
- **Gerador de receitas** — gera receitas a partir de ingredientes, culinária e restrições alimentares
- **Gerador de imagens** — cria imagens via DALL-E com controle de qualidade e resolução

## Stack

| Tecnologia | Uso |
|-----------|-----|
| Java 21 + Spring Boot 3.4.3 | Core da aplicação |
| Spring AI 1.0.0 | Abstração sobre a API OpenAI |
| OpenAI GPT | Chat completion |
| OpenAI DALL-E | Geração de imagens |

## Pré-requisitos

- JDK 21
- Chave de API da OpenAI

## Como executar

```bash
export OPEN_AI_API_KEY=sk-proj-...
cd spring-ai
./mvnw spring-boot:run
```

A API estará disponível em `http://localhost:8080`.

## Endpoints

### Chat Completion

```http
GET /ask-ai?message=Explique o que é Spring Boot
```

```http
GET /ask-ai-options?message=O que é injeção de dependência?&temperature=0.7&model=gpt-4o
```

### Geração de Receitas

```http
GET /recipe-creator?ingredients=frango,limão,alho&cuisine=brasileira&dietaryRestrictions=sem glúten
```

**Resposta:**
```
Frango Grelhado com Alho e Limão

Ingredientes:
- 500g de peito de frango
- 3 dentes de alho
...
```

### Geração de Imagens

```http
GET /generate-image?message=Uma cidade futurista ao pôr do sol&quality=hd&width=1024&height=1024&n=1
```

**Resposta:** URL da imagem gerada no DALL-E.

## Configuração

```yaml
# application.yml
spring:
  ai:
    openai:
      api-key: ${OPEN_AI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
      image:
        options:
          model: dall-e-3
```

## Arquitetura

```
src/main/java/com/sskings/spring_ai/
├── controller/
│   └── GenerativeAIController.java   # 4 endpoints
├── service/
│   ├── ChatService.java              # Lógica de chat
│   ├── ImageService.java             # Lógica de geração de imagens
│   └── RecipeService.java            # PromptTemplate para receitas
└── SpringAiApplication.java
```
