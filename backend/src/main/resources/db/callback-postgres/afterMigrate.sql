-- Revisão de segurança 5 (R5-4): só o dono das tabelas (usuário das migrações) mexe no histórico do Flyway.
-- Os privilégios padrão (deploy/db-init/01-roles.sh) dão SELECT/INSERT/UPDATE/DELETE ao usuário da API em TODA
-- tabela nova — inclusive nesta, que o Flyway cria depois. Só no PostgreSQL de produção (application-prod.yml).
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT DISTINCT grantee FROM information_schema.role_table_grants
             WHERE table_schema = 'public' AND table_name = 'flyway_schema_history' AND grantee <> current_user
    LOOP
        EXECUTE format('REVOKE ALL ON public.flyway_schema_history FROM %I', r.grantee);
    END LOOP;
END $$;
