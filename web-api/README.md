# Web API — Aplicação Web MVC

<p>
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.0-6DB33F?style=flat-square&logo=springboot&logoColor=white"/>
  <img src="https://img.shields.io/badge/Thymeleaf-Server--Side-005F0F?style=flat-square&logo=thymeleaf&logoColor=white"/>
  <img src="https://img.shields.io/badge/Bootstrap-5.3-7952B3?style=flat-square&logo=bootstrap&logoColor=white"/>
  <img src="https://img.shields.io/badge/MySQL-Database-4479A1?style=flat-square&logo=mysql&logoColor=white"/>
</p>

Aplicação web com renderização server-side (SSR) para gerenciamento de clientes, produtos e pedidos. Interface construída com Thymeleaf e Bootstrap 5, demonstrando o padrão MVC do Spring.

## Funcionalidades

- **Clientes**: cadastro, edição, listagem e remoção
- **Itens/Produtos**: gerenciamento do catálogo de produtos
- **Pedidos**: criação e acompanhamento de pedidos com autocomplete de clientes/itens
- Interface responsiva com Bootstrap 5.3
- Feedbacks visuais com flash messages após ações

## Stack

| Tecnologia | Uso |
|-----------|-----|
| Java 17 + Spring Boot 3.2.0 | Core da aplicação |
| Thymeleaf | Templates server-side |
| Spring Data JPA | Acesso ao banco |
| MySQL 8 | Banco de dados |
| Bootstrap 5.3.2 | CSS framework |
| jQuery 3.7.1 | JavaScript |
| EasyAutocomplete 1.3.6 | Autocomplete nos formulários |
| Spring Boot DevTools | Hot reload em desenvolvimento |

## Pré-requisitos

- JDK 17
- MySQL 8+

## Como executar

1. **Crie o banco de dados:**

```sql
CREATE DATABASE webapi;
```

2. **Configure a conexão** em `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/webapi
spring.datasource.username=seu_usuario
spring.datasource.password=sua_senha
spring.jpa.hibernate.ddl-auto=update
```

3. **Execute a aplicação:**

```bash
cd web-api
./mvnw spring-boot:run
```

Acesse em `http://localhost:8080`.

## Rotas

| Rota | Descrição |
|------|-----------|
| `GET /` | Página inicial |
| `GET /clientes` | Listagem de clientes |
| `GET /clientes/novo` | Formulário de cadastro |
| `POST /clientes` | Salvar novo cliente |
| `GET /clientes/{id}/editar` | Formulário de edição |
| `POST /clientes/{id}` | Atualizar cliente |
| `POST /clientes/{id}/deletar` | Remover cliente |
| `GET /itens` | Listagem de itens |
| `GET /pedidos` | Listagem de pedidos |

## Arquitetura MVC

```
src/main/java/com/sskings/webapi/
├── controllers/        # Controllers MVC (retornam views)
│   ├── IndexController.java
│   ├── ClienteController.java
│   ├── ItemController.java
│   └── PedidoController.java
├── services/           # Regras de negócio
├── repositories/       # Spring Data JPA
└── models/             # Entidades JPA

src/main/resources/
├── templates/          # Templates Thymeleaf (HTML)
│   ├── clientes/
│   ├── itens/
│   └── pedidos/
└── static/             # CSS, JS estáticos
```
