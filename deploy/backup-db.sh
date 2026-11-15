#!/usr/bin/env bash
# Backup cifrado do PostgreSQL de produção (docs/security-review.md, revisão 3, M6).
#
# - pg_dump (formato custom) de dentro do container "db", cifrado com AES-256 (openssl, PBKDF2) usando
#   BACKUP_PASSPHRASE — o dump tem dados pessoais (LGPD) e não pode ficar legível em disco nem fora dele;
# - confere o backup recém-gravado (decifra e lista o conteúdo com pg_restore --list);
# - apaga os backups mais velhos que BACKUP_KEEP_DAYS.
#
# Agende no cron do servidor (todo dia às 03:00):
#   0 3 * * *  /caminho/para/deploy/backup-db.sh >> /var/log/shopeasy-backup.log 2>&1
# e copie BACKUP_DIR para FORA do servidor (outro provedor/região). Restauração: deploy/restore-db.sh.
# Teste de restauração: rode o restore num servidor de homologação pelo menos uma vez por mês.
set -euo pipefail
cd "$(dirname "$0")"

ENV_FILE=${ENV_FILE:-.env.prod}
[ -r "$ENV_FILE" ] || { echo "Arquivo $ENV_FILE não encontrado" >&2; exit 1; }
# Lê as variáveis sem executar o arquivo (um "source" rodaria qualquer comando colado nele)
conf() { local v; v=$(grep -E "^$1=" "$ENV_FILE" | tail -n 1 | cut -d= -f2-) || true; printf '%s' "${v:-$2}"; }

PASSPHRASE=${BACKUP_PASSPHRASE:-$(conf BACKUP_PASSPHRASE "")}
DIR=${BACKUP_DIR:-$(conf BACKUP_DIR /var/backups/shopeasy)}
KEEP_DAYS=${BACKUP_KEEP_DAYS:-$(conf BACKUP_KEEP_DAYS 14)}
# dono das tabelas (revisão 5, R5-4): o usuário da API não tem permissão de recriar tabelas na restauração
DB_USER=$(conf DATABASE_MIGRATION_USERNAME "")
DB_NAME=$(conf DATABASE_NAME shopeasy)

if [ ${#PASSPHRASE} -lt 20 ]; then
  echo "BACKUP_PASSPHRASE ausente ou curta (mínimo 20 caracteres; gere com: openssl rand -base64 32)" >&2
  exit 1
fi
[[ "$KEEP_DAYS" =~ ^[0-9]+$ ]] || { echo "BACKUP_KEEP_DAYS inválido: $KEEP_DAYS" >&2; exit 1; }

compose() { docker compose -f docker-compose.prod.yml --env-file "$ENV_FILE" "$@"; }
cipher() { BACKUP_PASSPHRASE="$PASSPHRASE" openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -salt -pass env:BACKUP_PASSPHRASE "$@"; }

umask 077
mkdir -p "$DIR"
chmod 700 "$DIR"
out="$DIR/shopeasy-$(date -u +%Y%m%dT%H%M%SZ).dump.enc"
partial="$out.partial"
trap 'rm -f "$partial"' EXIT

compose exec -T db pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc | cipher -out "$partial"
cipher -d -in "$partial" | compose exec -T db pg_restore --list >/dev/null
mv "$partial" "$out"

find "$DIR" -maxdepth 1 -name 'shopeasy-*.dump.enc' -mtime +"$KEEP_DAYS" -print -delete | sed 's/^/removido (antigo): /'
echo "backup ok: $out ($(du -h "$out" | cut -f1))"
