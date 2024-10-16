CREATE TABLE endereco(
                         id bigint auto_increment not null primary key,
                         cliente_id bigint not null,
                         logradouro varchar(255) not null,
                         numero varchar(10) not null,
                         bairro varchar(100) not null,
                         complemento varchar(100) not null,
                         data_cadastro timestamp default current_timestamp,
                         constraint fk_cliente_endereco foreign key(cliente_id) references cliente(id) on delete cascade
);