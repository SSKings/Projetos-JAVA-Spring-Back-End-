# Voice Transcription — Áudio para Texto

<p>
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4.5-6DB33F?style=flat-square&logo=springboot&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring%20AI-1.0.0--RC1-6DB33F?style=flat-square&logo=spring&logoColor=white"/>
  <img src="https://img.shields.io/badge/OpenAI-Whisper-412991?style=flat-square&logo=openai&logoColor=white"/>
</p>

Serviço REST para transcrição de arquivos de áudio em texto, utilizando o modelo Whisper da OpenAI via Spring AI.

## Funcionalidade

Recebe um arquivo de áudio via upload multipart e retorna a transcrição como texto.

**Formatos suportados:** mp3, mp4, mpeg, mpga, m4a, wav, webm

## Stack

| Tecnologia | Uso |
|-----------|-----|
| Java 21 + Spring Boot 3.4.5 | Core da aplicação |
| Spring AI 1.0.0-RC1 | Integração com OpenAI |
| OpenAI Whisper | Modelo de transcrição de áudio |

## Pré-requisitos

- JDK 21
- Chave de API da OpenAI

## Como executar

```bash
export OPEN_AI_API_KEY=sk-proj-...
cd voice-transcription
./mvnw spring-boot:run
```

A API estará disponível em `http://localhost:8080`.

## Endpoint

### Transcrever áudio

```http
POST /ai/transcribe
Content-Type: multipart/form-data

file: <arquivo de áudio>
```

**Exemplo com cURL:**

```bash
curl -X POST http://localhost:8080/ai/transcribe \
  -F "file=@audio.mp3"
```

**Resposta:**
```
"Olá, esta é uma transcrição de exemplo gerada pelo modelo Whisper."
```

## Configuração

```yaml
# application.yml
spring:
  ai:
    openai:
      api-key: ${OPEN_AI_API_KEY}
      audio:
        transcription:
          options:
            model: whisper-1
            language: pt
  servlet:
    multipart:
      max-file-size: 25MB
      max-request-size: 25MB
```

## Arquitetura

```
src/main/java/com/sskings/voice_transcription/
├── controller/
│   └── TranscriptionController.java  # Upload e resposta
├── service/
│   └── TranscriptionService.java     # Integração com Whisper
├── config/
│   └── WebConfig.java                # Configuração de CORS e multipart
└── VoiceTranscriptionApplication.java
```
