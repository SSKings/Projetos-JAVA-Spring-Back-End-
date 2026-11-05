#!/usr/bin/env bash
# Simula o PSP avisando que um Pix foi pago (apenas para desenvolvimento/demonstração).
#
# Uso:  ./simulate-pix-payment.sh <txid> <valor>        ex.: ./simulate-pix-payment.sh K3J9...Q2 59.80
#
# O txid e o valor aparecem na tela de pagamento ("Identificador") e no admin. A assinatura é
# hex(HMAC-SHA256(PIX_WEBHOOK_SECRET, "<timestamp>.<corpo>")), igual à validada pelo back-end.
set -euo pipefail

TXID="${1:?informe o txid}"
AMOUNT="${2:?informe o valor, ex.: 59.80}"
API="${API_URL:-http://localhost:8080}"
SECRET="${PIX_WEBHOOK_SECRET:-dev-pix-webhook-secret-change-me}"

TS="$(date +%s)"
E2E="E$(date +%Y%m%d%H%M%S)SIMULADO$RANDOM"
BODY="{\"txid\":\"${TXID}\",\"endToEndId\":\"${E2E}\",\"amount\":${AMOUNT}}"
SIG="$(printf '%s' "${TS}.${BODY}" | openssl dgst -sha256 -hmac "${SECRET}" -hex | sed 's/^.* //')"

curl -sS -w '\nHTTP %{http_code}\n' -X POST "${API}/api/payments/pix/webhook" \
  -H 'Content-Type: application/json' \
  -H "X-Pix-Timestamp: ${TS}" \
  -H "X-Pix-Signature: ${SIG}" \
  -d "${BODY}"
