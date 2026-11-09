#!/usr/bin/env bash
# Restaura um backup gerado por backup-db.sh (docs/security-review.md, revisão 3, M6).
# SUBSTITUI os dados atuais do banco. Pare a API antes:  docker compose -f docker-compose.prod.yml stop api
#
# Uso:  ./restore-db.sh /var/backups/shopeasy/shopeasy-20261109T030000Z.dump.enc
#       (pede para digitar o nome do banco como confirmação; RESTORE_CONFIRM=<nome> pula a pergunta)
set -euo pipefail
cd "$(dirname "$0")"

backup=${1:?Informe o arquivo .dump.enc}
[ -r "$backup" ] || { echo "Arquivo não encontrado: $backup" >&2; exit 1; }
ENV_FILE=${ENV_FILE:-.env.prod}
[ -r "$ENV_FILE" ] || { echo "Arquivo $ENV_FILE não encontrado" >&2; exit 1; }
conf() { local v; v=$(grep -E "^$1=" "$ENV_FILE" | tail -n 1 | cut -d= -f2-) || true; printf '%s' "${v:-$2}"; }

PASSPHRASE=${BACKUP_PASSPHRASE:-$(conf BACKUP_PASSPHRASE "")}
DB_USER=$(conf DATABASE_USERNAME "")
DB_NAME=$(conf DATABASE_NAME shopeasy)
[ -n "$PASSPHRASE" ] || { echo "BACKUP_PASSPHRASE ausente" >&2; exit 1; }

answer=${RESTORE_CONFIRM:-}
if [ -z "$answer" ]; then
  read -r -p "Isto APAGA os dados atuais de '$DB_NAME'. Digite o nome do banco para continuar: " answer
fi
[ "$answer" = "$DB_NAME" ] || { echo "Confirmação não confere; nada foi alterado." >&2; exit 1; }

BACKUP_PASSPHRASE="$PASSPHRASE" openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -pass env:BACKUP_PASSPHRASE -in "$backup" \
  | docker compose -f docker-compose.prod.yml --env-file "$ENV_FILE" exec -T db \
      pg_restore -U "$DB_USER" -d "$DB_NAME" --clean --if-exists --no-owner --single-transaction
echo "restauração concluída a partir de $backup"
