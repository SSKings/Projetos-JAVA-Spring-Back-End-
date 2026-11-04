# Text to Speech — Síntese de Voz

<p>
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.2-6DB33F?style=flat-square&logo=springboot&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring%20AI-2.0.0--M2-6DB33F?style=flat-square&logo=spring&logoColor=white"/>
  <img src="https://img.shields.io/badge/OpenAI-TTS-412991?style=flat-square&logo=openai&logoColor=white"/>
</p>

Serviço REST de síntese de voz: converte texto em áudio MP3 usando a API de Text-to-Speech da OpenAI.

## Funcionalidade

Recebe um texto via JSON e retorna o arquivo de áudio sintetizado (`audio/mpeg`) pronto para reprodução ou download.

## Stack

| Tecnologia | Uso |
|-----------|-----|
| Java 17 + Spring Boot 4.0.2 | Core da aplicação |
| Spring AI 2.0.0-M2 | Integração com OpenAI TTS |
| OpenAI TTS | Motor de síntese de voz |

## Pré-requisitos

- JDK 17
- Chave de API da OpenAI

## Como executar

```bash
export OPEN_AI_API_KEY=sk-proj-...
cd text-to-speec
./mvnw spring-boot:run
```

A API estará disponível em `http://localhost:8080`.

## Endpoint

### Sintetizar texto em áudio

```http
POST /api/tts/synthesize
Content-Type: application/json

{
  "text": "Olá! Este é um exemplo de síntese de voz com Spring AI."
}
```

**Resposta:** arquivo `audio/mpeg` para download/reprodução.

**Exemplo com cURL:**

```bash
curl -X POST http://localhost:8080/api/tts/synthesize \
  -H "Content-Type: application/json" \
  -d '{"text": "Olá mundo!"}' \
  --output audio.mp3
```

## Configuração

```yaml
# application.yaml
spring:
  ai:
    openai:
      api-key: ${OPEN_AI_API_KEY}
      audio:
        speech:
          options:
            model: tts-1
            voice: alloy
            response-format: mp3
```

**Vozes disponíveis:** `alloy`, `echo`, `fable`, `onyx`, `nova`, `shimmer`

## Arquitetura

```
src/main/java/com/sskings/text_to_speec/
├── controller/
│   └── TextToSpeechController.java       # Endpoint de síntese
├── service/
│   └── PortableNarrationService.java     # Integração com OpenAI TTS
├── config/
│   └── WebConfig.java                    # Configuração de CORS
└── TextToSpeechApplication.java
```
