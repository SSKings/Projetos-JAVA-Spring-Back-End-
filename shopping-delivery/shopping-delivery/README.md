# Shopping Delivery

<p>
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3.7-6DB33F?style=flat-square&logo=springboot&logoColor=white"/>
  <img src="https://img.shields.io/badge/Flyway-Migrations-CC0200?style=flat-square&logo=flyway&logoColor=white"/>
  <img src="https://img.shields.io/badge/OpenAPI-Swagger-85EA2D?style=flat-square&logo=swagger&logoColor=black"/>
</p>

API REST de e-commerce com gestão completa de clientes, produtos, pedidos e entrega. Inclui controle de estoque em tempo real, cálculo automático de totais e ciclo de vida de pedidos.

## Funcionalidades

- **Clientes**: CRUD com validação de unicidade de e-mail e CPF
- **Endereços**: múltiplos endereços por cliente
- **Itens/Produtos**: catálogo com controle de estoque
- **Pedidos**: ciclo de vida completo com status rastreável
- **Itens de Pedido**: adição/remoção com atualização automática de estoque e total do pedido

## Regras de negócio

- Estoque é validado antes de adicionar um item ao pedido (`EstoqueInsuficienteException`)
- Estoque é decrementado ao adicionar e restaurado ao remover itens
- Total do pedido é recalculado automaticamente a cada alteração
- E-mail e CPF são únicos por cliente

### Ciclo de vida do pedido

```
PENDENTE → APROVADO → ENTREGUE
              ↘
           CANCELADO  (qualquer etapa)
```

## Stack

| Tecnologia | Uso |
|-----------|-----|
| Java 17 + Spring Boot 3.3.7 | Core da aplicação |
| Spring Data JPA | Acesso ao banco |
| Spring Validation (Jakarta) | Validação de entrada |
| Flyway | Migrações de banco versionadas |
| H2 Database | Banco em memória (dev) |
| SpringDoc OpenAPI | Documentação Swagger |
| Lombok | Redução de boilerplate |

## Como executar

```bash
cd shopping-delivery/shopping-delivery
./mvnw spring-boot:run
```

A API inicia em `http://localhost:8080` com banco H2 em memória.  
Swagger UI: `http://localhost:8080/swagger-ui.html`  
H2 Console: `http://localhost:8080/h2-console`

## Endpoints

### Clientes

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/clientes` | Criar cliente |
| `GET` | `/clientes` | Listar clientes |
| `GET` | `/clientes/{id}` | Buscar por ID |
| `PUT` | `/clientes/{id}` | Atualizar cliente |
| `DELETE` | `/clientes/{id}` | Remover cliente |

### Endereços

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/enderecos` | Criar endereço |
| `GET` | `/enderecos` | Listar endereços |
| `GET` | `/enderecos/{id}` | Buscar por ID |
| `PUT` | `/enderecos/{id}` | Atualizar endereço |
| `DELETE` | `/enderecos/{id}` | Remover endereço |

### Itens/Produtos

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/itens` | Criar item |
| `GET` | `/itens` | Listar itens |
| `GET` | `/itens/{id}` | Buscar por ID |
| `PUT` | `/itens/{id}` | Atualizar item |
| `DELETE` | `/itens/{id}` | Remover item |

### Pedidos

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/pedidos` | Criar pedido |
| `GET` | `/pedidos` | Listar pedidos |
| `GET` | `/pedidos/{id}` | Buscar por ID |
| `PUT` | `/pedidos/{id}` | Atualizar pedido |
| `PATCH` | `/pedidos/{id}/status` | Atualizar status |
| `DELETE` | `/pedidos/{id}` | Cancelar/remover pedido |

### Itens de Pedido

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/itens-pedido` | Adicionar item ao pedido |
| `GET` | `/itens-pedido` | Listar itens de pedido |
| `GET` | `/itens-pedido/{id}` | Buscar por ID |
| `DELETE` | `/itens-pedido/{id}` | Remover item do pedido |

## Arquitetura

```
src/main/java/com/sskings/shopping_delivery/
├── controllers/        # Endpoints REST (5 controllers)
├── services/           # Regras de negócio e cálculos
├── repositories/       # Spring Data JPA + queries customizadas
├── models/             # Entidades JPA + enum StatusPedido
├── exceptions/         # Exceções tipadas por domínio
└── doc/                # Configuração do Swagger

src/main/resources/db/migration/
├── V001__create_clientes.sql
├── V002__create_enderecos.sql
├── V003__create_itens.sql
├── V004__create_pedidos.sql
├── V005__create_itens_pedido.sql
├── V006__...
└── V007__...
```

## Testes

```bash
./mvnw test
```

Cobertura implementada:
- Testes de controller para todos os 5 controllers (MockMvc)
- Testes de service para todos os 5 services (Mockito)
- Teste de repositório (`ClienteRepositoryTest`)
