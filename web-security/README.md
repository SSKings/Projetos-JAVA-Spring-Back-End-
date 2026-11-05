# Web Security — Autenticação com Spring Security

<p>
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.0-6DB33F?style=flat-square&logo=springboot&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring%20Security-6-6DB33F?style=flat-square&logo=springsecurity&logoColor=white"/>
  <img src="https://img.shields.io/badge/BCrypt-Password%20Encoder-orange?style=flat-square"/>
  <img src="https://img.shields.io/badge/MySQL-Database-4479A1?style=flat-square&logo=mysql&logoColor=white"/>
</p>

Demonstração de autenticação e autorização com Spring Security 6: registro de usuários com senha criptografada (BCrypt), login com formulário, proteção de rotas e integração com Thymeleaf.

## Funcionalidades

- **Registro de usuários** com criptografia de senha via BCrypt
- **Login com formulário** gerenciado pelo Spring Security
- **Proteção de rotas** — acesso a `/home` e `/hello` requer autenticação
- **Logout** com redirecionamento para a página de login
- **Thymeleaf Security Extras** — controle de visibilidade na UI por autenticação
- `UserDetailsService` com persistência em banco de dados (MySQL)

## Stack

| Tecnologia | Uso |
|-----------|-----|
| Java 17 + Spring Boot 3.2.0 | Core da aplicação |
| Spring Security 6 | Autenticação e autorização |
| BCryptPasswordEncoder | Hash seguro de senhas |
| Spring Data JPA | Persistência de usuários |
| MySQL 8 | Banco de dados |
| Thymeleaf + Spring Security Extras | Templates com controle de acesso |

## Pré-requisitos

- JDK 17
- MySQL 8+

## Como executar

1. **Crie o banco de dados:**

```sql
CREATE DATABASE websecurity;
```

2. **Configure a conexão** em `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/websecurity
spring.datasource.username=seu_usuario
spring.datasource.password=sua_senha
spring.jpa.hibernate.ddl-auto=update
```

3. **Execute a aplicação:**

```bash
cd web-security
./mvnw spring-boot:run
```

Acesse em `http://localhost:8080`.

## Fluxo de autenticação

```
/register  →  POST /register  →  salva usuário com senha BCrypt  →  /login
/login     →  Spring Security valida via UserDetailsService     →  /home
/logout    →  Spring Security invalida sessão                   →  /login
```

## Rotas

| Rota | Método | Auth? | Descrição |
|------|--------|-------|-----------|
| `/login` | GET/POST | Não | Página de login |
| `/register` | GET/POST | Não | Registro de usuário |
| `/home` | GET | Sim | Página inicial após login |
| `/hello` | GET | Sim | Rota protegida de exemplo |

## Arquitetura

```
src/main/java/com/sskings/websecurity/
├── configs/
│   └── WebSecurityConfig.java        # SecurityFilterChain, PasswordEncoder, AuthenticationManager
├── controllers/
│   └── AuthenticationController.java # Login, register, home, hello
├── services/
│   └── AuthenticationService.java    # UserDetailsService — carrega usuário do banco
├── repositories/
│   └── UserRepository.java           # findByUsername
├── models/
│   └── User.java                     # Entidade JPA + UserDetails
└── WebSecurityApplication.java
```

## Conceitos demonstrados

- Configuração de `SecurityFilterChain` com Spring Security 6 (lambda DSL)
- `UserDetailsService` com repositório JPA (banco real, não in-memory)
- Injeção de `PasswordEncoder` como bean — sem instanciar diretamente nos controllers
- `AuthenticationManager` via `AuthenticationConfiguration`
- Formulário de login customizado com redirecionamento pós-login
