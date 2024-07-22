
-- TIPO (DOS LANÇAMENTOS)
INSERT INTO tipo_lancamento (id, nome) VALUES ('0fa994b2-6cdf-4dba-8a60-e503dc73aa3f', 'RECEITA');
INSERT INTO tipo_lancamento (id, nome) VALUES ('0fa334b2-6cdf-4dba-8a60-e503dc73aa3f', 'DESPESA');

-- FONTE (DOS LANÇAMENTOS)
INSERT INTO fonte_lancamento (id, nome) VALUES ('0fa224b2-6cdf-4dba-8a60-e503dc73aa3f', 'CARTAO');
INSERT INTO fonte_lancamento (id, nome) VALUES ('0fa994b2-67d3-4dba-8a60-e503dc73aa3f', 'CONTA');

-- USUÁRIOS-- Insere um usuário com role USER
INSERT INTO usuario (id, username, password, email, data_cadastro)
VALUES ( UUID(), 'user1', '$2a$12$FjVO6I3MBX0LtBPGPJRHOuNWwhPq12Q4yXGo46fmlyCILLw7jqb12', 'user1@example.com', '2023-07-22');

-- -- Insere mais um usuário com role USER
INSERT INTO usuario (id, username, password, email, data_cadastro)
VALUES (  UUID(), 'user2', '$2a$12$JB2s8elzyK7p10Ah3UqVV.ATM2pXS3pAU3cGb6.64X0wgJj2SLSTS', 'user2@example.com', '2023-07-22');

INSERT INTO usuario (id, username, password, role, email, data_cadastro)
VALUES ( UUID(), 'admin1', '$2a$12$ROVJ.xVmP9XitkB8NH2vv.th125rujhAPsGSWLblXv8QZS90g2otu', 'ADMIN','admin@gmail.com', '2023-07-22');