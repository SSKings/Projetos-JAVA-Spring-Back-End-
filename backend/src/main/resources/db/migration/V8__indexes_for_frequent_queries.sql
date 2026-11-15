-- Índices para as consultas frequentes (docs/architecture.md, "Índices do banco").
--
-- O PostgreSQL NÃO cria índice para chave estrangeira sozinho: até a V7 só a auditoria tinha índice, e cada
-- histórico de pedidos, checkout, página do painel e execução do job do Pix varria a tabela inteira. Cada índice
-- abaixo nasceu de uma consulta real do Hibernate, medida com 100 mil clientes e 1 milhão de pedidos
-- (antes → depois: ver a tabela na documentação). Sintaxe portável: roda igual no H2 (dev/testes) e no PostgreSQL.
-- IF NOT EXISTS: num banco grande, os índices podem ser criados antes com CREATE INDEX CONCURRENTLY (sem bloquear
-- escritas) e os mesmos nomes; a migração então só confirma.

-- pedidos do cliente: histórico, checkout (pendentes, unidades pendentes, Pix abandonados), exclusão de conta
CREATE INDEX IF NOT EXISTS idx_orders_user_status ON orders (user_id, status);
-- painel: lista de pedidos por status e lista geral, mais novos primeiro
CREATE INDEX IF NOT EXISTS idx_orders_status_created ON orders (status, created_at);
CREATE INDEX IF NOT EXISTS idx_orders_created ON orders (created_at);
-- "este endereço já foi usado em pedido?" (excluir endereço, excluir conta)
CREATE INDEX IF NOT EXISTS idx_orders_address ON orders (address_id);
-- itens de cada pedido (todo pedido mostrado carrega os itens)
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items (order_id);
-- job de expiração do Pix, a cada minuto: cobranças pendentes já vencidas
CREATE INDEX IF NOT EXISTS idx_payments_status_expires ON payments (status, expires_at);
-- endereços do cliente
CREATE INDEX IF NOT EXISTS idx_addresses_user ON addresses (user_id);
-- exportação LGPD (eventos do titular, mais novos primeiro) e anonimização da auditoria na exclusão da conta
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_created ON audit_logs (actor_id, created_at);
--
-- Sem índice de propósito (medido): a retenção diária de trusted_login_sources (29 ms, 1x/dia; o índice pesaria em
-- todo login) e a busca de produto por nome (7 ms com 5 mil produtos; LIKE '%x%' pede pg_trgm, que vale a pena
-- acima de ~50 mil produtos). O carrinho já usa o índice do UNIQUE (user_id, product_id).
