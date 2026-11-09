-- Dados de referência (tipos e fontes de lançamento): carregados em todos os perfis.
-- Usuários, contas e lançamentos de demonstração ficam em data-demo.sql, que NUNCA roda em produção
-- (revisão de segurança 3: o perfil production criava o usuário admin1 com senha de exemplo).

-- TIPO (DOS LANÇAMENTOS)
INSERT INTO tipo_lancamento (id, nome) VALUES ('0fa994b2-6cdf-4dba-8a60-e503dc73aa3f', 'RECEITA');
INSERT INTO tipo_lancamento (id, nome) VALUES ('0fa334b2-6cdf-4dba-8a60-e503dc73aa3f', 'DESPESA');

-- FONTE (DOS LANÇAMENTOS)
INSERT INTO fonte_lancamento (id, nome) VALUES ('0fa224b2-6cdf-4dba-8a60-e503dc73aa3f', 'CARTAO');
INSERT INTO fonte_lancamento (id, nome) VALUES ('0fa994b2-67d3-4dba-8a60-e503dc73aa3f', 'CONTA');
