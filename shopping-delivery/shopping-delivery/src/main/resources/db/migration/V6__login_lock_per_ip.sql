-- Revisão de segurança 3, A2: o bloqueio por tentativas deixou de ser da conta inteira (qualquer pessoa trancava o
-- dono, inclusive o admin) e passou a ser por email + IP, em memória (LoginAttemptService). As colunas ficaram sem uso.
ALTER TABLE users DROP COLUMN failed_login_attempts;
ALTER TABLE users DROP COLUMN locked_until;
