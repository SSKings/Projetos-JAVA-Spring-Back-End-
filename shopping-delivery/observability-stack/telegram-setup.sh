#!/usr/bin/env bash
# Configura as notificações dos alertas no Telegram, passo a passo (rode num terminal, dentro desta pasta):
#
#   ./telegram-setup.sh
#
# Antes: no Telegram, abra o @BotFather, envie /newbot e guarde o token que ele responder.
# O script: confere o token com o Telegram, descobre o seu chat ID (você manda /start ao bot), manda uma mensagem de
# teste, grava o .env (ignorado pelo Git, permissão 600), recria o Grafana com o Telegram e dispara o teste do
# contact point. O token nunca aparece na tela nem na linha de comando de outros processos.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"
ENV_FILE=.env
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"
TELEGRAM_API="${TELEGRAM_API:-https://api.telegram.org}"   # trocado só nos testes (servidor falso)

say()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
ok()   { printf '  \033[32m✔\033[0m %s\n' "$*"; }
die()  { printf '\n  \033[31m✘ %s\033[0m\n' "$*" >&2; exit 1; }

command -v python3 >/dev/null || die "python3 não encontrado"
command -v docker >/dev/null || die "docker não encontrado"

# Chama a API do Telegram sem expor o token em argv (curl lê a URL da entrada padrão). ensure_ascii=False: o formato
# de config do curl descarta a barra de escapes \uXXXX, e "✅ … vão" chegaria como "u2705 … vu00e3o".
telegram() {  # $1 = método, $2 = corpo JSON opcional
  local cfg="url = \"${TELEGRAM_API}/bot${TOKEN}/$1\""
  if [[ -n "${2:-}" ]]; then
    printf '%s\nheader = "Content-Type: application/json"\ndata = %s\n' "$cfg" "$(python3 -c 'import json,sys; print(json.dumps(sys.argv[1], ensure_ascii=False))' "$2")" \
      | curl -sS --max-time 20 --config -
  else
    printf '%s\n' "$cfg" | curl -sS --max-time 20 --config -
  fi
}

json_get() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"; }

# ---------------------------------------------------------------------------------------------------------------
say "1/5  Token do bot"
echo "  No Telegram: @BotFather → /newbot → copie o token (algo como 123456789:AAH...)."
read -rsp "  Cole o token e tecle Enter (ele não aparece na tela): " TOKEN || die "Entrada encerrada."; echo
TOKEN="$(printf '%s' "$TOKEN" | tr -d '[:space:]')"
[[ "$TOKEN" =~ ^[0-9]+:[A-Za-z0-9_-]+$ ]] || die "Isso não parece um token do BotFather (formato 123456789:AA...). Rode de novo."

me="$(telegram getMe)" || die "Sem acesso a api.telegram.org. Verifique a internet."
[[ "$(json_get "d.get('ok')" <<<"$me")" == "True" ]] || die "O Telegram recusou o token ($(json_get "d.get('description')" <<<"$me")). Confira com o @BotFather."
BOT="$(json_get "d['result']['username']" <<<"$me")"
ok "Token válido: bot @$BOT"

# ---------------------------------------------------------------------------------------------------------------
say "2/5  Chat que vai receber os alertas"
echo "  Abra https://t.me/$BOT no Telegram e envie /start (para um grupo: adicione o bot e mande uma mensagem lá)."
CHAT=""
for attempt in 1 2 3 4 5; do
  read -rp "  Depois de enviar, tecle Enter… " _ || die "Entrada encerrada."
  updates="$(telegram getUpdates)"
  mapfile -t chats < <(python3 -c '
import json, sys
seen = {}
for u in json.load(sys.stdin).get("result", []):
    m = u.get("message") or u.get("channel_post") or u.get("my_chat_member") or {}
    c = m.get("chat")
    if c:
        seen[c["id"]] = c.get("title") or " ".join(filter(None, [c.get("first_name"), c.get("last_name")])) or c.get("username") or "?"
for cid, name in seen.items():
    print(f"{cid}\t{name}")' <<<"$updates")
  if (( ${#chats[@]} == 1 )); then
    CHAT="${chats[0]%%$'\t'*}"; ok "Chat encontrado: ${chats[0]#*$'\t'} ($CHAT)"; break
  elif (( ${#chats[@]} > 1 )); then
    echo "  Encontrei mais de um chat:"
    for i in "${!chats[@]}"; do printf '    %d) %s (%s)\n' "$((i + 1))" "${chats[$i]#*$'\t'}" "${chats[$i]%%$'\t'*}"; done
    read -rp "  Número do chat que deve receber os alertas: " n || die "Entrada encerrada."
    [[ "$n" =~ ^[0-9]+$ ]] && (( n >= 1 && n <= ${#chats[@]} )) || die "Opção inválida."
    CHAT="${chats[$((n - 1))]%%$'\t'*}"; ok "Chat escolhido: $CHAT"; break
  fi
  echo "  Ainda não chegou nenhuma mensagem ao bot (tentativa $attempt de 5). Envie /start para @$BOT e tente de novo."
done
[[ -n "$CHAT" ]] || die "Não encontrei o chat. Envie /start para @$BOT e rode o script de novo."

# ---------------------------------------------------------------------------------------------------------------
say "3/5  Mensagem de teste direto pelo Telegram"
sent="$(telegram sendMessage "{\"chat_id\": $CHAT, \"text\": \"✅ ShopEasy: bot configurado. Os alertas do Grafana vão chegar aqui.\"}")"
[[ "$(json_get "d.get('ok')" <<<"$sent")" == "True" ]] || die "O Telegram não entregou a mensagem ($(json_get "d.get('description')" <<<"$sent"))."
ok "Mensagem enviada — confira no Telegram"

# ---------------------------------------------------------------------------------------------------------------
say "4/5  Gravando $ENV_FILE e recriando o Grafana com o Telegram"
if [[ -f "$ENV_FILE" ]] && grep -q '^TELEGRAM_BOT_TOKEN=' "$ENV_FILE"; then
  read -rp "  Já existe um $ENV_FILE com Telegram. Substituir? [s/N] " yn || die "Entrada encerrada."
  [[ "$yn" =~ ^[sS]$ ]] || die "Mantido o $ENV_FILE atual. Nada foi alterado."
fi
umask 077
{
  echo "# Gerado por telegram-setup.sh em $(date '+%Y-%m-%d %H:%M'). Não versionar (ver .gitignore)."
  echo "COMPOSE_FILE=docker-compose.yml:docker-compose.telegram.yml"
  echo "TELEGRAM_BOT_TOKEN=$TOKEN"
  echo "TELEGRAM_CHAT_ID=$CHAT"
} > "$ENV_FILE"
chmod 600 "$ENV_FILE"
ok "$ENV_FILE gravado (permissão 600)"
docker compose up -d grafana >/dev/null 2>&1 || { docker compose up -d grafana; die "docker compose falhou (mensagem acima)."; }
for _ in $(seq 1 60); do
  curl -fsS "$GRAFANA_URL/api/health" 2>/dev/null | grep -q '"database": *"ok"' && break
  [[ "$(docker inspect -f '{{.State.Running}}' grafana 2>/dev/null)" == "false" ]] && { docker logs --tail 5 grafana; die "O Grafana parou (log acima)."; }
  sleep 2
done
cp_json="$(curl -fsS "$GRAFANA_URL/api/v1/provisioning/contact-points" 2>/dev/null || true)"
grep -q '"Telegram ShopEasy"' <<<"$cp_json" || die "O Grafana subiu sem o contact point do Telegram. Veja: docker logs grafana"
ok "Grafana no ar com o contact point \"Telegram ShopEasy\""

# ---------------------------------------------------------------------------------------------------------------
say "5/5  Teste do contact point pelo Grafana (o mesmo botão Test da interface)"
body="$(python3 -c '
import json, sys
c = [x for x in json.load(sys.stdin) if x["name"] == "Telegram ShopEasy"][0]
print(json.dumps({"receivers": [{"name": c["name"], "grafana_managed_receiver_configs": [
    {"uid": c["uid"], "name": c["name"], "type": "telegram", "settings": c["settings"], "secureFields": {"bottoken": True}}]}]}))' <<<"$cp_json")"
result="$(curl -sS -X POST -H 'Content-Type: application/json' -d "$body" "$GRAFANA_URL/api/alertmanager/grafana/config/api/v1/receivers/test")"
status="$(json_get "d['receivers'][0]['grafana_managed_receiver_configs'][0]['status']" <<<"$result" 2>/dev/null || echo erro)"
[[ "$status" == "ok" ]] || die "O Grafana não conseguiu enviar: $(json_get "d['receivers'][0]['grafana_managed_receiver_configs'][0].get('error')" <<<"$result" 2>/dev/null || echo "$result")"
ok "O Grafana enviou a notificação de teste — confira no Telegram"

say "Pronto!"
cat <<EOF
  Os 12 alertas da API agora chegam em @$BOT.
  Para ver alertas de verdade:   ../load-tests/run.sh alerts
     → "Webhook Pix recusado" em ~1 min; força bruta, varredura e rate limit em ~3–4 min; as resoluções (✅) depois.
  Para desligar: apague a linha COMPOSE_FILE do .env e rode  docker compose up -d
EOF
