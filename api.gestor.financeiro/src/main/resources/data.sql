
-- TIPO (DOS LANÇAMENTOS)
INSERT INTO tipo_lancamento (id, nome) VALUES ('0fa994b2-6cdf-4dba-8a60-e503dc73aa3f', 'RECEITA');
INSERT INTO tipo_lancamento (id, nome) VALUES ('0fa334b2-6cdf-4dba-8a60-e503dc73aa3f', 'DESPESA');

-- FONTE (DOS LANÇAMENTOS)
INSERT INTO fonte_lancamento (id, nome) VALUES ('0fa224b2-6cdf-4dba-8a60-e503dc73aa3f', 'CARTAO');
INSERT INTO fonte_lancamento (id, nome) VALUES ('0fa994b2-67d3-4dba-8a60-e503dc73aa3f', 'CONTA');

-- Insere um usuário com role USER
INSERT INTO usuario (id, username, password, email, data_cadastro)
VALUES ( '16daec44-54f3-457c-bc31-c89cec0f725f', 'user1', '$2a$12$FjVO6I3MBX0LtBPGPJRHOuNWwhPq12Q4yXGo46fmlyCILLw7jqb12', 'user1@example.com', '2023-07-22');

-- -- Insere mais um usuário com role USER
INSERT INTO usuario (id, username, password, email, data_cadastro)
VALUES (  '2b261838-49a6-467f-aba8-91a20b29909e', 'user2', '$2a$12$JB2s8elzyK7p10Ah3UqVV.ATM2pXS3pAU3cGb6.64X0wgJj2SLSTS', 'user2@example.com', '2023-07-22');

-- -- Insere mais um usuário com role ADMIN
INSERT INTO usuario (id, username, password, role, email, data_cadastro)
VALUES ( '62c812cf-3a1b-45e5-bb9e-80cf3401ba83', 'admin1', '$2a$12$ROVJ.xVmP9XitkB8NH2vv.th125rujhAPsGSWLblXv8QZS90g2otu', 'ADMIN','admin@gmail.com', '2023-07-22');

-- -- -- -- Insere contas
INSERT INTO conta (id, numero, banco, usuario_id, saldo)
VALUES ('1ba994b2-6cdf-4dba-8a60-e503dc73aa5b', 324356879087, 'BANCO DO BRASIL', '16daec44-54f3-457c-bc31-c89cec0f725f', 1500.59);

INSERT INTO conta (id, numero, banco, usuario_id, saldo)
VALUES ('2ba994b2-6cdf-4dba-8a60-e503dc73aa5b', 324312312312, 'CAIXA','2b261838-49a6-467f-aba8-91a20b29909e', 1500.59);

INSERT INTO conta (id, numero, banco, usuario_id, saldo)
VALUES ('3ba994b2-6cdf-4dba-8a60-e503dc73aa5b', 324356879087, 'BRADESCO', '2b261838-49a6-467f-aba8-91a20b29909e', 1200.59);

INSERT INTO conta (id, numero, banco, usuario_id, saldo)
VALUES ('4ba994b2-6cdf-4dba-8a60-e503dc73aa5b', 324312312312, 'CAIXA','62c812cf-3a1b-45e5-bb9e-80cf3401ba83', 2500.59);

-- -- -- -- Insere cartões
INSERT INTO cartao_credito (id, numero, banco, vencimento, usuario_id, limite, limite_disponivel)
VALUES ('1ca994b2-6cdf-4dba-8a60-e503dc73aa5b', 324356879087, 'DIGIO', '2024-07-01', '16daec44-54f3-457c-bc31-c89cec0f725f', 1500.59, 1500.59);

INSERT INTO cartao_credito (id, numero, banco, vencimento, usuario_id, limite, limite_disponivel)
VALUES ('2ca994b2-6cdf-4dba-8a60-e503dc73aa5b', 324312312312, 'NUBANK', '2024-07-01', '16daec44-54f3-457c-bc31-c89cec0f725f', 1500.59, 1500.59);

INSERT INTO cartao_credito (id, numero, banco, vencimento, usuario_id, limite, limite_disponivel)
VALUES ('3ca994b2-6cdf-4dba-8a60-e503dc73aa5b', 324356879087, 'INTER', '2024-07-01', '2b261838-49a6-467f-aba8-91a20b29909e', 1200.59, 1200.59);

INSERT INTO cartao_credito (id, numero, banco, vencimento, usuario_id, limite, limite_disponivel)
VALUES ('4ca994b2-6cdf-4dba-8a60-e503dc73aa5b', 324312312312, 'NUBANK', '2024-07-01','62c812cf-3a1b-45e5-bb9e-80cf3401ba83', 2500.59, 2500.59);


-- Lançamento com conta
INSERT INTO lancamentos (id, valor, data_lancamento, usuario_id, tipo_id, fonte_id, cartao_id, conta_id, tipo)
VALUES ('8fe1846c-7532-41e1-8356-eddcd1b606ac', 500.00, '2024-07-20', '16daec44-54f3-457c-bc31-c89cec0f725f', '0fa994b2-6cdf-4dba-8a60-e503dc73aa3f', '0fa994b2-67d3-4dba-8a60-e503dc73aa3f', null, '1ba994b2-6cdf-4dba-8a60-e503dc73aa5b', 'SALDO');

-- Lançamento com cartão
INSERT INTO lancamentos (id, valor, data_lancamento, usuario_id, tipo_id, fonte_id, cartao_id, conta_id, tipo)
VALUES ('17566475-34a5-4b62-9164-5d0adceee815', 200.00, '2024-07-21', '2b261838-49a6-467f-aba8-91a20b29909e', '0fa334b2-6cdf-4dba-8a60-e503dc73aa3f', '0fa224b2-6cdf-4dba-8a60-e503dc73aa3f', '3ca994b2-6cdf-4dba-8a60-e503dc73aa5b', null, 'CREDITO');

-- Lançamento com conta
INSERT INTO lancamentos (id, valor, data_lancamento, usuario_id, tipo_id, fonte_id, cartao_id, conta_id, tipo)
VALUES ('fde09ee9-0f7e-44b8-a9fc-3f00683dd605', 750.00, '2024-07-22', '62c812cf-3a1b-45e5-bb9e-80cf3401ba83', '0fa994b2-6cdf-4dba-8a60-e503dc73aa3f', '0fa994b2-67d3-4dba-8a60-e503dc73aa3f', null, '4ba994b2-6cdf-4dba-8a60-e503dc73aa5b', 'SALDO');

-- Lançamento com cartão
INSERT INTO lancamentos (id, valor, data_lancamento, usuario_id, tipo_id, fonte_id, cartao_id, conta_id, tipo)
VALUES ('42f122c6-3d32-426f-976d-8cc783c7c0df', 320.00, '2024-07-23', '16daec44-54f3-457c-bc31-c89cec0f725f', '0fa334b2-6cdf-4dba-8a60-e503dc73aa3f', '0fa224b2-6cdf-4dba-8a60-e503dc73aa3f', '2ca994b2-6cdf-4dba-8a60-e503dc73aa5b', null, 'CREDITO');
