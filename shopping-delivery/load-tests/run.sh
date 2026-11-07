#!/usr/bin/env bash
# Roda os testes k6 da API via Docker (não precisa instalar o k6). Ver README.md desta pasta.
#
#   ./load-tests/run.sh smoke      # funcional: o fluxo de compra funciona? (segundos)
#   ./load-tests/run.sh load       # carga constante: latência com X ações/s (RATE, DURATION)
#   ./load-tests/run.sh stress     # estresse em degraus: qual o limite? (STEPS, STEP_SECONDS, SLO_MS)
#   ./load-tests/run.sh alerts     # dispara os alertas do Grafana de propósito (DRILL)
#
# Variáveis: BASE_URL (padrão http://localhost:8080) e as de cada teste; argumentos extras vão para o k6.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K6_IMAGE="grafana/k6:1.3.0"

usage() { sed -n '2,10p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 2; }
test_name="${1:-}"
case "$test_name" in smoke|load|stress|alerts) shift ;; *) usage ;; esac

if ! curl -fsS --max-time 5 "${BASE_URL:-http://localhost:8080}/actuator/health" >/dev/null; then
  echo "A API não respondeu em ${BASE_URL:-http://localhost:8080}/actuator/health. Ela está no ar?" >&2
  exit 1
fi

env_args=()
for var in BASE_URL RATE DURATION USERS STEPS STEP_SECONDS SLO_MS MAX_VUS DRILL; do
  if [[ -n "${!var:-}" ]]; then env_args+=(-e "$var=${!var}"); fi
done

mkdir -p "$DIR/results"
# --network host: o k6 alcança a API em localhost:8080 como um cliente desta máquina
exec docker run --rm -i --network host --user "$(id -u):$(id -g)" \
  -v "$DIR:/scripts" -w /scripts "${env_args[@]}" \
  "$K6_IMAGE" run "$@" "/scripts/$test_name.js"
