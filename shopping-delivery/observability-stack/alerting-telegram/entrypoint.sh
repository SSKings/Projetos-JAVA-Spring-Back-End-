#!/bin/sh
# Gera o contact point do Telegram a partir do ambiente e inicia o Grafana (docker-compose.telegram.yml).
#
# Por que não usar "$TELEGRAM_CHAT_ID" direto no arquivo de provisionamento: o Grafana substitui a variável e
# reinterpreta o resultado; um chat ID (sempre numérico) vira número e o campo "chatid" (texto) é recusado. Com
# provisionamento inválido o Grafana nem sobe. Aspas não resolvem: ficam literais no valor. Então este script
# valida os dois valores e gera o JSON pronto, numa cópia do provisionamento em /tmp (a pasta original é só leitura).
set -eu

# caminhos configuráveis só para o teste (scripts/test_grafana_provisioning.py); no container valem os padrões
TEMPLATE="${TELEGRAM_TEMPLATE:-/etc/grafana/telegram/telegram.template.json}"
SOURCE="${GF_PATHS_PROVISIONING:-/etc/grafana/provisioning}"
TARGET="${TELEGRAM_PROVISIONING_TARGET:-/tmp/grafana-provisioning}"
GRAFANA_RUN="${GRAFANA_RUN:-/run.sh}"

fail() { echo "telegram: $*" >&2; exit 1; }

token="${TELEGRAM_BOT_TOKEN:-}"
chat="${TELEGRAM_CHAT_ID:-}"
# validar também impede que um valor com / & ou aspas quebre o sed ou o JSON abaixo
printf '%s' "$token" | grep -Eq '^[0-9]+:[A-Za-z0-9_-]+$' \
  || fail "TELEGRAM_BOT_TOKEN inválido: esperado o token do @BotFather (123456789:AA...). Veja .env.example"
printf '%s' "$chat" | grep -Eq '^-?[0-9]+$' \
  || fail "TELEGRAM_CHAT_ID inválido: esperado um número (grupos são negativos, ex.: -1001234567890). Veja .env.example"

rm -rf "$TARGET"
cp -R "$SOURCE" "$TARGET"
mkdir -p "$TARGET/alerting"
sed -e "s/__TELEGRAM_BOT_TOKEN__/$token/" -e "s/__TELEGRAM_CHAT_ID__/$chat/" "$TEMPLATE" > "$TARGET/alerting/telegram.json"
chmod 600 "$TARGET/alerting/telegram.json"

export GF_PATHS_PROVISIONING="$TARGET"
exec "$GRAFANA_RUN" "$@"
