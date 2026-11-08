#!/usr/bin/env bash
# Configura o bot de ADMINISTRAÇÃO no Telegram (avisos de pedidos + gestão de pedidos e usuários), passo a passo:
#
#   ./scripts/telegram-admin-bot-setup.sh
#
# Antes: no Telegram, abra o @BotFather, envie /newbot e guarde o token. Use um bot NOVO, diferente do bot dos
# alertas do Grafana (observability-stack/telegram-setup.sh): este aqui recebe comandos e mostra dados de clientes.
#
# O script confere o token, descobre o seu chat ID (você manda /start ao bot), manda uma mensagem de teste e grava
# shopping-delivery/.env.telegram-admin-bot (ignorado pelo Git, permissão 600). O run-backend.sh lê esse arquivo:
# depois é só reiniciar a API. Rodar de novo com o mesmo bot ADICIONA outro admin à lista.
# O token nunca aparece na tela nem na linha de comando de outros processos.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
ENV_FILE=.env.telegram-admin-bot
ALERTS_ENV=observability-stack/.env
TELEGRAM_API="${TELEGRAM_API:-https://api.telegram.org}"   # trocado só nos testes (servidor falso)

say()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
ok()   { printf '  \033[32m✔\033[0m %s\n' "$*"; }
die()  { printf '\n  \033[31m✘ %s\033[0m\n' "$*" >&2; exit 1; }

command -v python3 >/dev/null || die "python3 não encontrado"
command -v curl >/dev/null || die "curl não encontrado"

# Chama a API do Telegram sem expor o token em argv (curl lê a URL da entrada padrão).
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
env_get() { [[ -f "$2" ]] && sed -n "s/^$1=//p" "$2" | tail -n 1 || true; }

# ---------------------------------------------------------------------------------------------------------------
say "1/4  Token do bot de administração"
echo "  No Telegram: @BotFather → /newbot → copie o token (algo como 123456789:AAH...)."
echo "  Use um bot diferente do bot dos alertas do Grafana."
read -rsp "  Cole o token e tecle Enter (ele não aparece na tela): " TOKEN || die "Entrada encerrada."; echo
TOKEN="$(printf '%s' "$TOKEN" | tr -d '[:space:]')"
[[ "$TOKEN" =~ ^[0-9]+:[A-Za-z0-9_-]{30,}$ ]] || die "Isso não parece um token do BotFather (formato 123456789:AA...). Rode de novo."
[[ "$TOKEN" != "$(env_get TELEGRAM_BOT_TOKEN "$ALERTS_ENV")" ]] \
  || die "Esse é o bot dos alertas do Grafana. Crie outro bot no @BotFather para a administração."

me="$(telegram getMe)" || die "Sem acesso a api.telegram.org. Verifique a internet."
[[ "$(json_get "d.get('ok')" <<<"$me")" == "True" ]] || die "O Telegram recusou o token ($(json_get "d.get('description')" <<<"$me")). Confira com o @BotFather."
BOT="$(json_get "d['result']['username']" <<<"$me")"
ok "Token válido: bot @$BOT"

# ---------------------------------------------------------------------------------------------------------------
say "2/4  Conta de admin e chat"
read -rp "  E-mail da conta ADMIN da loja que vai usar o bot [admin@shopeasy.com]: " EMAIL || die "Entrada encerrada."
EMAIL="$(printf '%s' "${EMAIL:-admin@shopeasy.com}" | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
[[ "$EMAIL" =~ ^[^@:,]+@[^@:,]+$ ]] || die "E-mail inválido."

echo "  Agora abra https://t.me/$BOT no Telegram (com a conta de quem vai administrar) e envie /start."
echo "  Precisa ser a conversa privada com o bot: em grupos o bot não atende."
CHAT=""
for attempt in 1 2 3 4 5; do
  read -rp "  Depois de enviar, tecle Enter… " _ || die "Entrada encerrada."
  updates="$(telegram getUpdates)"
  mapfile -t chats < <(python3 -c '
import json, sys
seen = {}
for u in json.load(sys.stdin).get("result", []):
    c = (u.get("message") or {}).get("chat")
    if c and c.get("type") == "private":
        seen[c["id"]] = " ".join(filter(None, [c.get("first_name"), c.get("last_name")])) or c.get("username") or "?"
for cid, name in seen.items():
    print(f"{cid}\t{name}")' <<<"$updates")
  if (( ${#chats[@]} == 1 )); then
    CHAT="${chats[0]%%$'\t'*}"; ok "Chat encontrado: ${chats[0]#*$'\t'} ($CHAT)"; break
  elif (( ${#chats[@]} > 1 )); then
    echo "  Encontrei mais de um chat:"
    for i in "${!chats[@]}"; do printf '    %d) %s (%s)\n' "$((i + 1))" "${chats[$i]#*$'\t'}" "${chats[$i]%%$'\t'*}"; done
    read -rp "  Número do chat de $EMAIL: " n || die "Entrada encerrada."
    [[ "$n" =~ ^[0-9]+$ ]] && (( n >= 1 && n <= ${#chats[@]} )) || die "Opção inválida."
    CHAT="${chats[$((n - 1))]%%$'\t'*}"; ok "Chat escolhido: $CHAT"; break
  fi
  # Com a API já rodando este bot, é ela quem lê as mensagens — e responde ao /start com o chat ID.
  echo "  Não vi o /start (tentativa $attempt de 5). Se a API já está com este bot ligado, ela respondeu no"
  read -rp "  Telegram \"Seu chat ID é …\": digite esse número, ou só Enter para tentar de novo: " typed || die "Entrada encerrada."
  if [[ "$typed" =~ ^[0-9]+$ ]]; then CHAT="$typed"; ok "Chat informado: $CHAT"; break; fi
done
[[ -n "$CHAT" ]] || die "Não encontrei o chat. Envie /start para @$BOT e rode o script de novo."

# ---------------------------------------------------------------------------------------------------------------
say "3/4  Mensagem de teste"
sent="$(telegram sendMessage "{\"chat_id\": $CHAT, \"text\": \"✅ ShopEasy: este chat vai administrar a loja como $EMAIL. Reinicie a API e mande /ajuda.\"}")"
[[ "$(json_get "d.get('ok')" <<<"$sent")" == "True" ]] || die "O Telegram não entregou a mensagem ($(json_get "d.get('description')" <<<"$sent"))."
ok "Mensagem enviada — confira no Telegram"

# ---------------------------------------------------------------------------------------------------------------
say "4/4  Gravando $ENV_FILE"
ADMINS="$CHAT:$EMAIL"
if [[ -f "$ENV_FILE" ]]; then
  if [[ "$(env_get TELEGRAM_ADMIN_BOT_TOKEN "$ENV_FILE")" == "$TOKEN" ]]; then
    # mesmo bot: mantém os outros admins e troca/adiciona este chat
    ADMINS="$(python3 -c '
import sys
entries = [e.strip() for e in sys.argv[1].split(",") if e.strip() and not e.strip().startswith(sys.argv[2] + ":")]
print(",".join(entries + [sys.argv[2] + ":" + sys.argv[3]]))' "$(env_get TELEGRAM_ADMIN_BOT_ADMINS "$ENV_FILE")" "$CHAT" "$EMAIL")"
    ok "Mesmo bot: admins agora são $ADMINS"
  else
    read -rp "  Já existe um $ENV_FILE com outro bot. Substituir? [s/N] " yn || die "Entrada encerrada."
    [[ "$yn" =~ ^[sS]$ ]] || die "Mantido o $ENV_FILE atual. Nada foi alterado."
  fi
fi
umask 077
{
  echo "# Gerado por scripts/telegram-admin-bot-setup.sh em $(date '+%Y-%m-%d %H:%M'). Não versionar (ver .gitignore)."
  echo "# Lido pelo scripts/run-backend.sh. Em produção, as mesmas variáveis vão no deploy/.env.prod."
  echo "TELEGRAM_ADMIN_BOT_ENABLED=true"
  echo "TELEGRAM_ADMIN_BOT_TOKEN=$TOKEN"
  echo "TELEGRAM_ADMIN_BOT_ADMINS=$ADMINS"
} > "$ENV_FILE"
chmod 600 "$ENV_FILE"
ok "$ENV_FILE gravado (permissão 600)"

say "Pronto!"
cat <<EOF
  1. Reinicie a API:  ./scripts/run-backend.sh   (o log mostra "Telegram: bot de administração iniciado")
  2. No Telegram, mande /ajuda para @$BOT.
  A conta $EMAIL precisa ser ADMIN e estar ativa (em produção, também com MFA ativado).
  Para desligar: apague $ENV_FILE (ou troque TELEGRAM_ADMIN_BOT_ENABLED para false) e reinicie a API.
EOF
