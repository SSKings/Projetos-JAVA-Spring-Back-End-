#!/usr/bin/env bash
# Roda os testes k6 da API via Docker (não precisa instalar o k6). Ver README.md desta pasta.
#
#   ./load-tests/run.sh smoke      # funcional: o fluxo de compra funciona? (segundos)
#   ./load-tests/run.sh load       # carga constante: latência com X ações/s (RATE, DURATION)
#   ./load-tests/run.sh stress     # estresse em degraus: qual o limite? (STEPS, STEP_SECONDS, SLO_MS)
#   ./load-tests/run.sh alerts     # dispara os alertas do Grafana de propósito (DRILL)
#   ./load-tests/run.sh edge       # limite por IP da borda (nginx de produção; ver prod-like/)
#
# Variáveis: BASE_URL (padrão http://localhost:8080) e as de cada teste; argumentos extras vão para o k6.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Por digest (revisão 5, R5-8). Sem comentário no fim da linha: o CI lê o valor com sed (ci.yml, "k6 inspect").
K6_IMAGE="grafana/k6:1.3.0@sha256:3ddc8b1a33a2c3d8edc6e99b6a762ae36cba08788463458f5e6a7703e14eb77d"

usage() { sed -n '2,11p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 2; }
test_name="${1:-}"
case "$test_name" in smoke|load|stress|alerts|edge) shift ;; *) usage ;; esac

# HEALTH_URL: atrás do nginx de produção o /actuator não é exposto (prod-like/prod-like.sh usa /api/products)
HEALTH_URL="${HEALTH_URL:-${BASE_URL:-http://localhost:8080}/actuator/health}"
insecure=(); [[ "${K6_INSECURE_SKIP_TLS_VERIFY:-}" == true ]] && insecure=(-k)   # certificado autoassinado
if ! curl -fsS "${insecure[@]}" --max-time 5 "$HEALTH_URL" >/dev/null; then
  echo "A API não respondeu em $HEALTH_URL. Ela está no ar?" >&2
  exit 1
fi

env_args=()
for var in BASE_URL RATE DURATION USERS BUYERS MIX MAX_REGISTER PROD_LIKE STEPS STEP_SECONDS SLO_MS MAX_VUS DRILL \
           K6_INSECURE_SKIP_TLS_VERIFY; do
  if [[ -n "${!var:-}" ]]; then env_args+=(-e "$var=${!var}"); fi
done

mkdir -p "$DIR/results"
# --network host: o k6 alcança a API em localhost:8080 como um cliente desta máquina
exec docker run --rm -i --network host --user "$(id -u):$(id -g)" \
  -v "$DIR:/scripts" -w /scripts "${env_args[@]}" \
  "$K6_IMAGE" run "$@" "/scripts/$test_name.js"
