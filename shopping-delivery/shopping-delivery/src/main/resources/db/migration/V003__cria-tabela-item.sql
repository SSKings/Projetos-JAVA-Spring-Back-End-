CREATE TABLE item(
                     id bigint auto_increment not null primary key,
                     nome varchar(100) not null,
                     url_image varchar(255) not null,
                     descricao varchar(255) not null,
                     estoque int not null,
                     preco_unitario DECIMAL(10,2) not null
);