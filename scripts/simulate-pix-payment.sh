#!/usr/bin/env bash
# Simula o PSP avisando que um Pix foi pago (apenas para desenvolvimento/demonstração).
#
# Uso:
#   ./simulate-pix-payment.sh '<pix copia e cola>'          # txid e valor são lidos do próprio código
#   ./simulate-pix-payment.sh '<pix copia e cola>' <valor>  # força outro valor (ex.: testar valor errado)
#   ./simulate-pix-payment.sh <txid> <valor>                # ex.: ./simulate-pix-payment.sh KG55CN64XV6ZTA5KF7G46C8CN 49.90
#   --dry-run (em qualquer posição): mostra o que seria enviado, sem enviar
#
# O copia e cola tem espaços ("Pedido 1", "Sao Paulo"): use aspas. Sem aspas o script ainda tenta juntar
# as partes, mas aspas evitam surpresas. O txid também aparece na tela de pagamento ("Identificador").
#
# A assinatura é hex(HMAC-SHA256(PIX_WEBHOOK_SECRET, "<timestamp>.<corpo>")), igual à validada pelo back-end.
# Variáveis: API_URL (padrão http://localhost:8080), PIX_WEBHOOK_SECRET (padrão: o segredo do perfil dev).
set -euo pipefail

usage() {
  sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//' >&2
  exit 2
}
fail() {
  echo "erro: $*" >&2
  echo "use --help para ver o uso" >&2
  exit 2
}

DRY_RUN=0
ARGS=()
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    -h|--help) usage ;;
    *) ARGS+=("$arg") ;;
  esac
done
[ "${#ARGS[@]}" -gt 0 ] || usage

AMOUNT_RE='^[0-9]+(\.[0-9]{1,2})?$'
TXID_RE='^[A-Za-z0-9]{1,25}$'

# CRC16/CCITT-FALSE (poly 0x1021, init 0xFFFF) — o mesmo do BR Code (PixBrCode.crc16 no back-end)
crc16() {
  local data="$1" crc=0xFFFF byte i
  for byte in $(printf '%s' "$data" | od -An -v -tu1); do
    crc=$(( crc ^ (byte << 8) ))
    for i in 1 2 3 4 5 6 7 8; do
      if (( crc & 0x8000 )); then crc=$(( ((crc << 1) ^ 0x1021) & 0xFFFF )); else crc=$(( (crc << 1) & 0xFFFF )); fi
    done
  done
  printf '%04X' "$crc"
}

# Lê um campo EMV (ID de 2 dígitos + tamanho de 2 dígitos + valor) de um payload
emv_field() {
  local payload="$1" wanted="$2" i=0 id len
  while (( i + 4 <= ${#payload} )); do
    id="${payload:i:2}"; len="${payload:i+2:2}"
    [[ "$len" =~ ^[0-9]{2}$ ]] || return 1
    if [ "$id" = "$wanted" ]; then printf '%s' "${payload:i+4:10#$len}"; return 0; fi
    i=$(( i + 4 + 10#$len ))
  done
  return 1
}

if [[ "${ARGS[0]}" == 000201* ]]; then
  # copia e cola: um valor final opcional (override); o resto é o código (partes sem aspas são reunidas)
  OVERRIDE=""
  if [ "${#ARGS[@]}" -gt 1 ] && [[ "${ARGS[-1]}" =~ $AMOUNT_RE ]]; then
    OVERRIDE="${ARGS[-1]}"
    unset 'ARGS[-1]'
  fi
  PAYLOAD="${ARGS[*]}"
  [ "${#PAYLOAD}" -gt 8 ] || fail "código Pix muito curto"
  EXPECTED_CRC="${PAYLOAD: -4}"
  ACTUAL_CRC="$(crc16 "${PAYLOAD:0:${#PAYLOAD}-4}")"
  [ "${PAYLOAD: -8:4}" = "6304" ] && [ "$EXPECTED_CRC" = "$ACTUAL_CRC" ] \
    || fail "código Pix inválido ou incompleto (o CRC no fim do código não confere). Copie o código inteiro e use aspas."
  ADDITIONAL="$(emv_field "$PAYLOAD" 62)" || fail "o código Pix não tem o campo 62 (txid)"
  TXID="$(emv_field "$ADDITIONAL" 05)" || fail "o código Pix não tem txid (campo 62/05)"
  AMOUNT="${OVERRIDE:-$(emv_field "$PAYLOAD" 54 || true)}"
  [ -n "$AMOUNT" ] || fail "o código Pix não tem valor (campo 54); informe o valor como segundo argumento"
else
  [ "${#ARGS[@]}" -eq 2 ] || fail "esperado <txid> <valor> ou '<pix copia e cola>' (recebidos ${#ARGS[@]} argumentos)"
  TXID="${ARGS[0]}"
  AMOUNT="${ARGS[1]}"
fi

[[ "$TXID" =~ $TXID_RE ]] || fail "txid inválido: '${TXID}' (1 a 25 letras/números)"
[[ "$AMOUNT" =~ $AMOUNT_RE ]] || fail "valor inválido: '${AMOUNT}' (use ponto, ex.: 49.90)"

API="${API_URL:-http://localhost:8080}"
SECRET="${PIX_WEBHOOK_SECRET:-dev-pix-webhook-secret-change-me}"
TS="$(date +%s)"
E2E="E$(date +%Y%m%d%H%M%S)SIMULADO$RANDOM"
BODY="{\"txid\":\"${TXID}\",\"endToEndId\":\"${E2E}\",\"amount\":${AMOUNT}}"
SIG="$(printf '%s' "${TS}.${BODY}" | openssl dgst -sha256 -hmac "${SECRET}" -hex | sed 's/^.* //')"

echo "txid: ${TXID} | valor: ${AMOUNT} | destino: ${API}/api/payments/pix/webhook" >&2
if [ "$DRY_RUN" -eq 1 ]; then
  echo "$BODY"
  exit 0
fi

curl -sS -w '\nHTTP %{http_code}\n' -X POST "${API}/api/payments/pix/webhook" \
  -H 'Content-Type: application/json' \
  -H "X-Pix-Timestamp: ${TS}" \
  -H "X-Pix-Signature: ${SIG}" \
  -d "${BODY}"
