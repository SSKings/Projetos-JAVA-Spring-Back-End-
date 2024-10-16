CREATE TABLE pedido(
                       id bigint auto_increment not null primary key,
                       cliente_id bigint not null,
                       endereco_entrega_id bigint not null,
                       data_pedido timestamp default current_timestamp,
                       status varchar(50) default 'PENDENTE',
                       total DECIMAL(10,2) default 0.00,
                       constraint fk_cliente_pedido foreign key(cliente_id) references cliente(id),
                       constraint fk_endereco_pedido foreign key(endereco_entrega_id) references endereco(id)
);