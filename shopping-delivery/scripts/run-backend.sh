#!/usr/bin/env bash
# Sobe a API com o agente Java do OpenTelemetry (traces, métricas e logs → collector da observability-stack).
# Baseado em docs/instrunctions-cloud-impl-observability.md (seção 2.3), com estas mudanças (ADR-022):
#   - versão do agente FIXADA no pom.xml (otel-javaagent.version) e baixada pelo Maven do Maven Central com
#     verificação de checksum, em vez de "releases/latest" sem verificação (qualquer release nova, ou um
#     download adulterado, rodaria dentro da JVM com acesso a tudo);
#   - endpoint 4318 (http/protobuf, o protocolo padrão do agente 2.x; a especificação apontava 4317 = gRPC);
#   - variáveis podem ser sobrescritas pelo ambiente; argumentos extras vão para a aplicação.
#
# Uso (com a stack no ar: cd ../observability-stack && docker compose up -d):
#   ./scripts/run-backend.sh                    # perfil dev
#   SKIP_BUILD=1 ./scripts/run-backend.sh       # reaproveita o jar já gerado
#   ./scripts/run-backend.sh --server.port=8081
#
# Bot de administração no Telegram (ADR-024): se existir shopping-delivery/.env.telegram-admin-bot (gerado por
# scripts/telegram-admin-bot-setup.sh), as variáveis TELEGRAM_ADMIN_BOT_* dele são carregadas. As que já estiverem
# no ambiente têm prioridade.
set -euo pipefail

BOT_ENV="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.env.telegram-admin-bot"
if [[ -f "$BOT_ENV" ]]; then
  while IFS='=' read -r key value; do
    if [[ "$key" =~ ^TELEGRAM_ADMIN_BOT_[A-Z]+$ && -z "${!key:-}" ]]; then export "$key=$value"; fi
  done < "$BOT_ENV"
  echo "==> Bot de administração no Telegram: configuração lida de .env.telegram-admin-bot"
fi

BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../shopping-delivery" && pwd)"
cd "$BACKEND_DIR"

AGENT_VERSION="$(sed -n 's:.*<otel-javaagent.version>\(.*\)</otel-javaagent.version>.*:\1:p' pom.xml)"
if [[ -z "$AGENT_VERSION" ]]; then
  echo "otel-javaagent.version não encontrada no pom.xml" >&2
  exit 1
fi
AGENT_DIR="target/otel"
AGENT_JAR="$AGENT_DIR/opentelemetry-javaagent-$AGENT_VERSION.jar"

if [[ ! -f "$AGENT_JAR" ]]; then
  echo "==> Baixando o agente OpenTelemetry $AGENT_VERSION (Maven Central, checksum verificado)"
  # -C (--strict-checksums): falha se o SHA do Maven Central não bater
  ./mvnw -q -C dependency:copy \
    -Dartifact="io.opentelemetry.javaagent:opentelemetry-javaagent:$AGENT_VERSION" \
    -DoutputDirectory="$AGENT_DIR"
fi

if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  echo "==> Gerando o jar da API (testes rodam no CI e em ./mvnw verify)"
  # -C: o mesmo vale para as dependências da aplicação
  ./mvnw -q -C clean package -DskipTests
  # clean apagou o agente: copia de novo (do repositório local, já verificado)
  ./mvnw -q -o dependency:copy \
    -Dartifact="io.opentelemetry.javaagent:opentelemetry-javaagent:$AGENT_VERSION" \
    -DoutputDirectory="$AGENT_DIR"
fi

APP_JAR="$(ls target/*.jar | grep -v -- '-plain\.jar$' | head -n 1)"

export OTEL_SERVICE_NAME="${OTEL_SERVICE_NAME:-backend-spring}"
export OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT:-http://localhost:4318}"
export OTEL_EXPORTER_OTLP_PROTOCOL="${OTEL_EXPORTER_OTLP_PROTOCOL:-http/protobuf}"
export OTEL_TRACES_EXPORTER="${OTEL_TRACES_EXPORTER:-otlp}"
export OTEL_METRICS_EXPORTER="${OTEL_METRICS_EXPORTER:-otlp}"
export OTEL_LOGS_EXPORTER="${OTEL_LOGS_EXPORTER:-otlp}"
export OTEL_METRIC_EXPORT_INTERVAL="${OTEL_METRIC_EXPORT_INTERVAL:-15000}"
export OTEL_RESOURCE_ATTRIBUTES="${OTEL_RESOURCE_ATTRIBUTES:-deployment.environment.name=${SPRING_PROFILES_ACTIVE:-dev}}"

echo "==> $OTEL_SERVICE_NAME → $OTEL_EXPORTER_OTLP_ENDPOINT ($APP_JAR)"
exec java -javaagent:"$AGENT_JAR" -jar "$APP_JAR" "$@"
