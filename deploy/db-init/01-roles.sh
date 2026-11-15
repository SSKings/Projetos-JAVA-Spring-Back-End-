#!/usr/bin/env bash
# Papéis do PostgreSQL com privilégio mínimo (docs/security-review.md, revisão 5, R5-4).
#
#   superusuário (POSTGRES_USER)       → só cria o banco e roda este script; a aplicação nunca o usa
#   DATABASE_MIGRATION_USERNAME (dono) → dono do schema e das tabelas; o Flyway migra com ele (DDL)
#   DATABASE_USERNAME (aplicação)      → só SELECT/INSERT/UPDATE/DELETE; não cria, altera nem apaga tabelas
#
# Antes, a API usava o superusuário: qualquer falha futura de SQL viraria controle do banco inteiro (e, via
# COPY ... PROGRAM, execução de comandos no container).
#
# O entrypoint do postgres roda os scripts de /docker-entrypoint-initdb.d só quando o volume está vazio. Banco já
# existente: deploy/db-upgrade-roles.sh. Idempotente: pode rodar de novo.
set -euo pipefail
: "${DATABASE_MIGRATION_USERNAME:?}" "${DATABASE_MIGRATION_PASSWORD:?}" "${DATABASE_USERNAME:?}" "${DATABASE_PASSWORD:?}"
if [ "$DATABASE_USERNAME" = "$DATABASE_MIGRATION_USERNAME" ] || [ "$DATABASE_USERNAME" = "$POSTGRES_USER" ]; then
  echo "DATABASE_USERNAME precisa ser diferente do usuário de migração e do superusuário" >&2
  exit 1
fi

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v owner="$DATABASE_MIGRATION_USERNAME" -v owner_pw="$DATABASE_MIGRATION_PASSWORD" \
     -v app="$DATABASE_USERNAME" -v app_pw="$DATABASE_PASSWORD" -v db="$POSTGRES_DB" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN', :'owner') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'owner') \gexec
SELECT format('CREATE ROLE %I LOGIN', :'app')   WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app') \gexec
ALTER ROLE :"owner" NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'owner_pw';
ALTER ROLE :"app"   NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'app_pw';

REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE :"db" TO :"owner";
GRANT CONNECT ON DATABASE :"db" TO :"app";

REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO :"owner";
GRANT USAGE ON SCHEMA public TO :"app";

-- tabelas e sequências que já existem (banco antigo) passam para o dono; a aplicação recebe só DML
SELECT format('ALTER TABLE public.%I OWNER TO %I', tablename, :'owner') FROM pg_tables WHERE schemaname = 'public' \gexec
SELECT format('ALTER SEQUENCE public.%I OWNER TO %I', sequencename, :'owner') FROM pg_sequences
 WHERE schemaname = 'public' AND NOT EXISTS (
   SELECT 1 FROM pg_depend d JOIN pg_class c ON c.oid = d.objid
    WHERE c.relname = sequencename AND d.deptype IN ('a', 'i')) \gexec
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"app";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO :"app";
-- histórico de migrações: só o dono (banco antigo; num banco novo o Flyway cria a tabela depois, ver README)
SELECT format('REVOKE ALL ON public.flyway_schema_history FROM %I', :'app')
 WHERE to_regclass('public.flyway_schema_history') IS NOT NULL \gexec

-- o que o Flyway criar daqui para frente
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO :"app";
SQL
echo "papéis do banco configurados: dono=$DATABASE_MIGRATION_USERNAME, aplicação=$DATABASE_USERNAME"
