#!/usr/bin/env bash
# Testes do simulate-pix-payment.sh (não precisa da API: usa --dry-run).
# Uso: ./scripts/simulate-pix-payment.test.sh
set -uo pipefail
cd "$(dirname "$0")"
SCRIPT=./simulate-pix-payment.sh

# BR Code real gerado pelo back-end em dev (chave pix@shopeasy.example, valor 49.90, CRC F71A)
CODE='00020101021226540014br.gov.bcb.pix0120pix@shopeasy.example0208Pedido 1520400005303986540549.905802BR5908ShopEasy6009Sao Paulo62290525KG55CN64XV6ZTA5KF7G46C8CN6304F71A'
TXID='KG55CN64XV6ZTA5KF7G46C8CN'

PASS=0; FAIL=0
check() {  # check <descrição> <saída/erro esperado (regex)> <args...>
  local name="$1" expected="$2"; shift 2
  local out; out="$("$SCRIPT" --dry-run "$@" 2>&1)"
  if [[ "$out" =~ $expected ]]; then PASS=$((PASS+1)); echo "ok   - $name"
  else FAIL=$((FAIL+1)); echo "FAIL - $name"; echo "       esperado: $expected"; echo "       obtido:   $out"; fi
}

# shellcheck disable=SC2086 # sem aspas de propósito: reproduz o erro de uso que motivou a mudança
check "copia e cola SEM aspas + valor (o shell divide o código)" "\"txid\":\"$TXID\".*\"amount\":49\.90" $CODE 49.90
check "copia e cola com aspas, valor lido do código"           "\"txid\":\"$TXID\".*\"amount\":49\.90" "$CODE"
check "copia e cola com valor forçado (testar valor errado)"    "\"amount\":1\.00"                       "$CODE" 1.00
check "formato <txid> <valor>"                                  "\"txid\":\"$TXID\".*\"amount\":49\.90" "$TXID" 49.90
check "código cortado é recusado pelo CRC"                      "CRC no fim do código não confere"      "${CODE:0:120}"
check "CRC adulterado é recusado"                               "CRC no fim do código não confere"      "${CODE%F71A}0000"
check "valor com vírgula é recusado"                            "valor inválido: '49,90'"               "$TXID" 49,90
check "txid com caractere inválido é recusado"                  "txid inválido"                         "TX-1" 10.00
check "argumentos a mais no formato antigo são recusados"       "recebidos 3 argumentos"                "$TXID" 10.00 extra
check "sem argumentos mostra o uso"                             "Uso:"

echo "$PASS passaram, $FAIL falharam"
[ "$FAIL" -eq 0 ]
