#!/usr/bin/env bash
# Sobe a PILHA DE PRODUÇÃO (deploy/: PostgreSQL + API perfil prod + nginx com TLS) localmente, para testes de carga.
# Projeto Docker separado (shopeasy-loadtest), segredos descartáveis, porta só em 127.0.0.1. Ver ../README.md.
#
#   ./load-tests/prod-like/prod-like.sh up        # gera segredos/certificado/nginx, constrói e sobe (~5 min na 1ª vez)
#   ./load-tests/prod-like/prod-like.sh stress    # estresse com a mistura completa + CPU/memória de cada container
#   ./load-tests/prod-like/prod-like.sh smoke     # teste funcional através do nginx (TLS)
#   ./load-tests/prod-like/prod-like.sh edge      # limite por IP do nginx (com NGINX_LIMITS=prod no up)
#   ./load-tests/prod-like/prod-like.sh down      # derruba e APAGA os dados (volumes) do teste
#
# Variáveis: LOADTEST_HTTPS_PORT (8443), API_MEM_LIMIT (1536m), DATABASE_POOL_SIZE (10, como na produção),
# NGINX_LIMITS=prod (mantém os limites reais do nginx: mostra o efeito do rate limit por IP); as do stress.js.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SD="$(cd "$HERE/../.." && pwd)"                  # shopping-delivery/
GEN="$HERE/generated"
PORT="${LOADTEST_HTTPS_PORT:-8443}"
BASE="https://localhost:$PORT"
compose() {
  docker compose -p shopeasy-loadtest -f "$SD/deploy/docker-compose.prod.yml" -f "$HERE/docker-compose.loadtest.yml" \
    --env-file "$GEN/.env.loadtest" "$@"
}
secret() { openssl rand -base64 "$1" | tr -d '\n'; }

generate() {
  mkdir -p "$GEN/certs"; chmod 700 "$GEN"
  if [[ ! -f "$GEN/.env.loadtest" ]]; then
    umask 077
    cat > "$GEN/.env.loadtest" <<ENV
# Gerado por prod-like.sh — descartável, só para teste de carga local. NÃO usar em produção.
DATABASE_NAME=shopeasy
DATABASE_USERNAME=shopeasy
DATABASE_PASSWORD=$(secret 24)
JWT_SECRET=$(secret 48)
APP_ENCRYPTION_KEY=$(secret 32)
JWT_EXPIRATION_MINUTES=120
CORS_ALLOWED_ORIGINS=https://localhost:$PORT
ADMIN_NAME=Administrador
ADMIN_EMAIL=admin@loadtest.local
ADMIN_PASSWORD=Adm$(secret 18 | tr -dc 'A-Za-z0-9')9
REQUIRE_ADMIN_MFA=true
PIX_KEY=12345678000195
PIX_MERCHANT_NAME=ShopEasy Teste
PIX_MERCHANT_CITY=Sao Paulo
PIX_EXPIRATION_MINUTES=30
PIX_WEBHOOK_SECRET=$(secret 48)
PIX_WEBHOOK_ALLOWED_IPS=
TERMS_VERSION=2026-11-05
DATABASE_POOL_SIZE=${DATABASE_POOL_SIZE:-10}
ENV
    umask 022
  fi
  if [[ ! -f "$GEN/certs/fullchain.pem" ]]; then
    openssl req -x509 -newkey rsa:2048 -nodes -days 7 -subj "/CN=localhost" \
      -keyout "$GEN/certs/privkey.pem" -out "$GEN/certs/fullchain.pem" 2>/dev/null
    chmod 644 "$GEN/certs/"*.pem   # o nginx do container roda sem root
  fi
  # nginx: cópia EXATA do de produção; só os limites por IP sobem (a menos que NGINX_LIMITS=prod)
  { echo "# GERADO por prod-like.sh a partir de deploy/nginx.conf — só para teste de carga."
    if [[ "${NGINX_LIMITS:-teste}" == prod ]]; then cat "$SD/deploy/nginx.conf"
    else sed -E -e 's/(limit_req_zone .* rate=)[0-9]+r\/[sm]/\1100000r\/s/' -e 's/burst=[0-9]+ nodelay/burst=100000 nodelay/' "$SD/deploy/nginx.conf"; fi
  } > "$GEN/nginx.conf"
  grep -q "limit_req zone=api" "$GEN/nginx.conf" || { echo "nginx.conf inesperado (limit_req sumiu)" >&2; exit 1; }
}

wait_ready() {
  echo "==> esperando a API responder por $BASE ..."
  for _ in $(seq 1 120); do
    curl -ksf --max-time 3 "$BASE/api/products?size=1" >/dev/null 2>&1 && { echo "==> pronto"; return 0; }
    sleep 3
  done
  compose logs --tail 40 api >&2; exit 1
}

# Amostra CPU/memória dos containers do teste a cada 2 s, até o arquivo-sinal sumir
sample_stats() {
  local out="$1" flag="$2"
  echo "hora,container,cpu_pct,mem_mib" > "$out"
  while [[ -f "$flag" ]]; do
    docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' $(compose ps -q) 2>/dev/null \
      | awk -F, -v t="$(date +%H:%M:%S)" '{gsub("%","",$2); split($3,m," "); v=m[1]; u=v; gsub(/[0-9.]/,"",u); gsub(/[A-Za-z]/,"",v);
          if (u=="GiB") v*=1024; if (u=="KiB") v/=1024; n=$1; sub(/^shopeasy-loadtest-/,"",n); sub(/-1$/,"",n); printf "%s,%s,%s,%.0f\n", t, n, $2, v}' >> "$out"
    sleep 2
  done
}

summarize_stats() {
  python3 - "$1" <<'PY'
import csv, sys
from collections import defaultdict
rows = list(csv.DictReader(open(sys.argv[1])))
by = defaultdict(list)
for r in rows: by[r["container"]].append((float(r["cpu_pct"] or 0), float(r["mem_mib"] or 0)))
print(f"\nRECURSOS DOS CONTAINERS (amostras a cada ~2 s; 100% = 1 núcleo; a máquina tem {__import__('os').cpu_count()} threads)")
print("container   CPU média   CPU pico   memória pico")
for name, vals in sorted(by.items()):
    cpus = [c for c, _ in vals]
    print(f"{name:10} {sum(cpus)/len(cpus):8.0f}%  {max(cpus):8.0f}%  {max(m for _, m in vals):9.0f} MiB")
PY
}

case "${1:-}" in
  up)
    generate
    compose up -d --build
    # o nginx.conf é regravado no mesmo arquivo montado: o container já de pé só enxerga a mudança com um reload
    compose exec -T web nginx -s reload >/dev/null 2>&1 || true
    wait_ready
    echo "==> limites do nginx: ${NGINX_LIMITS:-teste} ($(grep -o 'zone=api:10m rate=[^;]*' "$GEN/nginx.conf"))"
    echo "Pilha de produção no ar em $BASE (certificado autoassinado). Derrube com: $0 down" ;;
  smoke)
    HEALTH_URL="$BASE/api/products?size=1" PROD_LIKE=1 BASE_URL="$BASE" K6_INSECURE_SKIP_TLS_VERIFY=true \
      "$SD/load-tests/run.sh" smoke "${@:2}" ;;
  stress|load)
    mkdir -p "$SD/load-tests/results"
    stats="$SD/load-tests/results/prod-like-recursos-$(date +%Y%m%d-%H%M%S).csv"; flag="$(mktemp)"
    sample_stats "$stats" "$flag" & sampler=$!
    set +e
    HEALTH_URL="$BASE/api/products?size=1" MIX="${MIX:-completa}" BASE_URL="$BASE" K6_INSECURE_SKIP_TLS_VERIFY=true \
      MAX_REGISTER=1000 "$SD/load-tests/run.sh" "$1" "${@:2}"
    rc=$?
    set -e
    rm -f "$flag"; wait "$sampler" 2>/dev/null || true
    summarize_stats "$stats"; echo "(amostras em $stats)"
    exit $rc ;;
  edge)
    HEALTH_URL="$BASE/api/products?size=1" BASE_URL="$BASE" K6_INSECURE_SKIP_TLS_VERIFY=true "$SD/load-tests/run.sh" edge "${@:2}" ;;
  down)
    compose down -v --remove-orphans
    echo "Pilha de teste removida (dados apagados). Segredos/certificado continuam em $GEN (apague a pasta se quiser)." ;;
  *) sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
