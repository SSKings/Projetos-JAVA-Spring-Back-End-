CREATE TABLE item_pedido(
                            id bigint auto_increment not null primary key,
                            pedido_id bigint not null,
                            item_id bigint not null,
                            quantidade bigint not null,
                            preco_unitario DECIMAL(10,2) not null,
                            subtotal DECIMAL(10,2) AS (quantidade * preco_unitario) STORED,
                            constraint fk_pedido_item_pedido foreign key(pedido_id) references pedido(id) on delete cascade,
                            constraint fk_item_item_pedido foreign key(item_id) references item(id) on delete cascade
);