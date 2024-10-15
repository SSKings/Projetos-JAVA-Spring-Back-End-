CREATE TABLE cliente(
                        id bigint auto_increment not null primary key,
                        nome varchar(100) not null,
                        email varchar(100) not null,
                        telefone varchar(15) not null,
                        cpf varchar(11) not null,
                        data_cadastro timestamp default current_timestamp

);