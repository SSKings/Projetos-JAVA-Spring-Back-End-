# ShopEasy — Revisão de segurança e prontidão para produção

Data: 2026-11-05 · Escopo: `shopping-delivery/shopping-delivery` (API) e `shopping-delivery-frontend` (SPA).
**Revisão 2: 2026-11-06** (sobre o código já corrigido) — ver [seção 6](#6-revisão-2--2026-11-06).

**Método**
- Revisão do código: autenticação, autorização, validação, pagamento e configuração.
- Testes dinâmicos contra a API rodando localmente, perfil de desenvolvimento.
- Análise das versões das dependências.

Cada achado indica se foi **confirmado** (reproduzido) ou **por análise** (lido no código, não explorado).

> Este documento é técnico. Os pontos legais (seção 4) são um roteiro e **não substituem advogado e contador**.

---

## 1. Resumo

| # | Severidade | Achado | Status |
|---|------------|--------|--------|
| C1 | Crítica | Segredos e credenciais padrão versionados em repositório **público** (JWT, webhook Pix, admin) | Confirmado: token de admin forjado → 200 |
| C2 | Crítica | Condição de corrida no checkout: vende acima do estoque | Confirmado: estoque 1 → 8 pedidos |
| A1 | Alta | Sem limite de tentativas em login/cadastro/webhook | Confirmado: 40 logins errados, nenhum bloqueio |
| A2 | Alta | Enumeração de usuários (mensagem do cadastro e tempo do login) | Confirmado: 5 ms vs 87 ms |
| A3 | Alta | Spring Boot 3.3.7 fora do suporte OSS; Tomcat/Spring Security com CVEs corrigidos depois | Por análise |
| A4 | Alta | Pix estático + confirmação manual: valor editável e risco de comprovante falso | Por análise |
| A5 | Alta | Ambiente de dev não é implantável: sem TLS, H2 em memória (perde dados), Vite dev server | Por análise |
| M1 | Média | JWT em `localStorage`, sem CSP e sem revogação (logout não invalida o token) | Por análise |
| M2 | Média | CORS aceita qualquer origem | Confirmado |
| M3 | Média | Swagger/OpenAPI público | Confirmado: 200 pela rede |
| M4 | Média | Console H2 habilitado (acesso remoto **bloqueado** pelo H2) | Confirmado: remoto recusado |
| M5 | Média | Migrações Flyway incompatíveis com PostgreSQL (`AUTO_INCREMENT`) | Por análise |
| M6 | Média | Sem trilha de auditoria das ações de admin e de pagamento | Por análise |
| M7 | Média | Política de senha só no front; sem verificação de email, recuperação de senha ou MFA no admin | Por análise |
| M8 | Média | Webhook Pix só com HMAC (sem mTLS/allowlist); corrida webhook × expiração sem lock | Por análise |
| B1 | Baixa | `ProductRequest`: preço sem máximo (estoura `DECIMAL(10,2)` → 500), `imageUrl` aceita qualquer esquema | Por análise |
| B2 | Baixa | Front sem CSP/HSTS/Referrer-Policy (dependem do servidor de produção) | Por análise |

**Pontos positivos encontrados**
- BCrypt para senhas.
- Toda consulta de carrinho, endereço e pedido filtra pelo usuário logado: sem IDOR. Exemplos: `findByIdAndUserId` e `findByOrderIdAndUserId`.
- Preço e valor do Pix calculados no servidor.
- Bean Validation nos DTOs.
- Erro 500 genérico, sem stack trace.
- Usuário bloqueado perde o acesso na hora.
- Webhook com HMAC em tempo constante e janela contra replay.
- Console H2 recusa conexões remotas.
- `npm audit`: 0 vulnerabilidades.

---

## 1.1 Status das correções (atualizado em 2026-11-05)

Legenda: ✅ corrigido e testado · 🟡 mitigado (correção completa depende de terceiros) · ⏳ pendente (fora do código)

| # | Status | O que foi feito | Como foi verificado |
|---|--------|-----------------|---------------------|
| C1 | ✅ | Segredos saíram do `application.yml` (só `application-dev.yml` tem valores de dev); perfil `prod` exige variáveis e o `ProductionConfigValidator` recusa valores ausentes, curtos ou conhecidos, inclusive o segredo que vazou | Startup em `prod` com valores antigos **falha** listando 6 problemas; token forjado com o segredo antigo → **401** (`SecurityHardeningIntegrationTest`, teste manual em PostgreSQL) |
| C2 | ✅ | Baixa de estoque com `UPDATE … WHERE stock_quantity >= :qty` atômico; devolução com incremento atômico; `@Version` em `Order` e `Payment` (conflito → 409) | 10 checkouts simultâneos para 3 unidades → exatamente 3 pedidos e estoque 0 (`concurrentCheckoutNeverOversells`) |
| A1 | ✅ | `RateLimitFilter` (20/min por IP em login/cadastro, 120/min no webhook) + bloqueio de 15 min após 5 falhas (persistido no banco) + `limit_req` no nginx | 21ª tentativa → 429 em `prod`; conta bloqueada → 429 com `Retry-After` (`bruteForceLocksAccount`, `RateLimitFilterTest`) |
| A2 | 🟡 | Login executa BCrypt também para email inexistente (tempo igual); cadastro limitado pelo rate limit | `login_unknownEmail`. A mensagem "email já em uso" no cadastro continua: eliminar exige **verificação de email** (precisa de um provedor de email/SMTP) |
| A3 | ✅ | Spring Boot 3.3.7 → **3.5.16** (Tomcat, Spring Framework 6.2 e Spring Security corrigidos), springdoc 2.8.17; Dependabot para Maven, npm, Docker e Actions, com minor/patch agrupadas e majors ignoradas (ADR-020) | `./mvnw verify` e `npm run coverage` verdes. Migrar para o Spring Boot 4.x é o próximo passo (mudança grande) |
| A4 | 🟡 | Webhook com allowlist de IPs do PSP (`PIX_WEBHOOK_ALLOWED_IPS`), rejeições auditadas, estorno registrado (`REFUNDED`); bloco de mTLS pronto no nginx | `webhook_ipAllowlist`. **Pix dinâmico (`cob`) e mTLS exigem contrato com um PSP** |
| A5 | ✅ | Perfil `prod` com PostgreSQL; `Dockerfile` da API (usuário sem root) e do front (nginx sem root); `deploy/` com nginx (TLS, HSTS, CSP, rate limit) e `docker-compose.prod.yml` | Pilha completa subida com Docker (ver ADR-019) |
| M1 | 🟡 | `tokenVersion` no JWT: logout, bloqueio, troca de papel e MFA revogam **todos** os tokens; CSP estrita no nginx; 401 (sessão) separado de 403 (permissão) | `logoutRevokesTokens`. Token continua em `localStorage`; refresh token em cookie `HttpOnly` fica como evolução |
| M2 | ✅ | `CORS_ALLOWED_ORIGINS` com origens exatas (`*` só em dev); em produção a API fica atrás do nginx na mesma origem | Origem maliciosa sem `Access-Control-Allow-Origin` em `prod` |
| M3/M4 | ✅ | Swagger e console H2 desligados por padrão (ligados só em `dev`); `web.ignoring` só quando habilitados; nginx devolve 404 | `/v3/api-docs`, `/swagger-ui`, `/h2-console` → 401 em `prod` |
| M5 | ✅ | Migrações com `GENERATED BY DEFAULT AS IDENTITY`; driver PostgreSQL + `flyway-database-postgresql` | V1–V5 aplicadas em PostgreSQL 16 e `ddl-auto: validate` ok |
| M6 | ✅ | Tabela `audit_logs` (V5): login, bloqueio, MFA, status de pedido, Pix, estorno, usuários, produtos, exportação e exclusão de dados; tela **Admin → Auditoria**; access log do Tomcat com `max-days: 190` | `AuditServiceTest`, eventos gravados em PostgreSQL, arquivo de access log gerado |
| M7 | ✅/🟡 | Política de senha no back-end (8+, letras e números); **MFA TOTP** (RFC 6238, segredo cifrado com AES-256-GCM) obrigatório para ADMIN em `prod`; reautenticação para excluir conta e desligar MFA | Vetores da RFC 6238, `forgedTokenAndAdminMfa`. Verificação de email e "esqueci a senha" dependem de SMTP |
| M8 | ✅ | Lock otimista em `Payment`/`Order` (webhook × job × admin) | `optimisticLock_returns409` |
| B1 | ✅ | `ProductRequest`: preço ≤ 99.999.999,99 com 2 casas, estoque ≤ 1.000.000, descrição ≤ 2000, imagem só `https://`; erros 500 agora são registrados no log | Testes de validação |
| B2 | ✅ | nginx com HSTS, CSP, X-Frame-Options, Referrer-Policy e Permissions-Policy; API com CSP `default-src 'none'` | Cabeçalhos conferidos na resposta |

**Conformidade (seção 4):**
- ✅ Implementado:
  - aceite versionado de termos e privacidade no cadastro;
  - **Minha conta → Dados e privacidade**: corrigir dados, baixar todos os dados em JSON e excluir a conta (anonimização que preserva os pedidos por obrigação legal);
  - cancelamento pelo cliente antes do envio;
  - **direito de arrependimento** em até 7 dias da entrega, com registro do estorno;
  - rodapé com identificação do fornecedor;
  - modelos de política de privacidade, termos de uso e trocas e devoluções;
  - registros de acesso guardados por 6 meses.
- ⏳ Depende da empresa:
  - preencher CNPJ, razão social, endereço e contatos (`VITE_COMPANY_*`);
  - revisão jurídica dos textos;
  - emissão de NF-e;
  - conta PJ com chave Pix do CNPJ;
  - frete e prazos;
  - email transacional (confirmação de pedido);
  - contratos com operadores;
  - registro das operações de tratamento;
  - plano de resposta a incidentes;
  - pentest externo;
  - auditoria WCAG.

---

## 2. Achados e correções

### C1 — Segredos e credenciais padrão no repositório público
`application.yml` traz valores padrão:
- `security.jwt.secret: ecommerce-super-secret-key-change-in-production-2024`
- `PIX_WEBHOOK_SECRET` → `dev-pix-webhook-secret-change-me`
- `ADMIN_PASSWORD` → `admin123`

O repositório `SSKings/Projetos-JAVA-Spring-Back-End-` é **público** e o segredo JWT está no histórico desde `a60f2a5`. Se a aplicação subir sem as variáveis de ambiente, ela roda calada com esses valores:
- **JWT:** qualquer pessoa assina um token `sub=admin@shopeasy.com` e vira administrador. Teste feito: `GET /api/admin/users` com token forjado → **200**.
- **Webhook:** qualquer pessoa assina e marca um pedido como pago sem ter pagado.
- **Admin:** `admin@shopeasy.com` / `admin123` → **200**.

**Correção**
1. Remover os padrões de `application.yml` e criar `application-dev.yml` só com valores de dev.
2. No perfil `prod`, falhar no startup se algum segredo estiver vazio, for um valor de dev conhecido ou for curto (JWT e webhook com no mínimo 32 bytes aleatórios).
3. `AdminSeeder` em produção: exigir senha forte vinda de variável ou desativar o seed e criar o admin por comando único.
4. Guardar os segredos num cofre (AWS Secrets Manager, GCP Secret Manager, Vault ou secrets do provedor) e definir a rotação.
5. Ativar secret scanning no GitHub e `gitleaks` no CI. Considerar todos esses valores comprometidos para sempre: nunca reutilizá-los.

### C2 — Venda acima do estoque (lost update)
`OrderService.checkout` lê `stockQuantity`, compara e grava `stock - qty` sem lock nem versão. Teste feito: produto com estoque 1 e 8 checkouts simultâneos → **8 × 201**, 8 pedidos para 1 unidade. O mesmo padrão existe entre webhook e `PixExpirationJob` em `Payment`/`Order`.

**Correção**
- Fazer a baixa atômica no banco e conferir as linhas afetadas:
  ```sql
  UPDATE products SET stock_quantity = stock_quantity - :qty
  WHERE id = :id AND stock_quantity >= :qty
  ```
  Se nenhuma linha for afetada → `BusinessException("Insufficient stock")` e rollback.
- Alternativa: `@Version` em `Product`, `Order` e `Payment` (lock otimista) com nova tentativa, ou `PESSIMISTIC_WRITE` nas leituras do checkout, do webhook e do job.
- Teste de concorrência com o banco real (ex.: Testcontainers + PostgreSQL, `ExecutorService` com N checkouts).

### A1 — Sem limite de tentativas
40 logins errados seguidos responderam 400, sem 429 e sem bloqueio. O mesmo vale para cadastro (criação de contas em massa) e webhook.

**Correção**
- Rate limit por IP e por conta: Bucket4j, ou no proxy/gateway (nginx `limit_req`, Cloudflare/WAF). Ex.: 5 tentativas por minuto por conta e 20 por minuto por IP.
- Atraso progressivo e bloqueio temporário após N falhas.
- CAPTCHA após falhas repetidas.
- Alerta para picos.

### A2 — Enumeração de usuários
- O cadastro responde `Email already in use`.
- O login com email inexistente responde em ~5 ms e com email existente em ~87 ms (BCrypt só roda quando o usuário existe).

**Correção**
- No login, rodar `passwordEncoder.matches` contra um hash fictício quando o usuário não existe.
- No cadastro, responder sempre "verifique seu email" e enviar ao dono do endereço "você já tem conta" (exige verificação de email, ver M7).

### A3 — Dependências
- Spring Boot **3.3.7**, que traz Tomcat 10.1.34, Spring Framework 6.1.16 e Spring Security 6.3.6. A linha 3.3 já está fora do suporte OSS.
- Há CVEs públicos corrigidos em versões posteriores, por exemplo:
  - **CVE-2025-24813** (Tomcat, só com escrita no servlet padrão, que não é o default);
  - **CVE-2025-22228** (Spring Security: senhas BCrypt com mais de 72 bytes; mitigado em parte pelo `@Size(max = 72)` do cadastro).
- Não rodei um scanner de CVE sobre a árvore Maven.

**Correção**
- Migrar para a linha suportada mais recente do Spring Boot (confirmar em spring.io/projects/spring-boot#support) e Java 21 LTS.
- Ativar Dependabot/Renovate e rodar `dependency-check-maven` (ou OSV-Scanner) e CodeQL no CI, quebrando o build em CVSS ≥ 7.

### A4 — Pix estático
- **Valor editável:** o QR Code estático leva a chave da loja; dependendo do app, o cliente consegue mudar o valor. O webhook recusa valor divergente, mas a **confirmação manual depende de uma pessoa** conferir o extrato.
- **Comprovante falso:** é uma fraude comum ("mandei o comprovante").

**Correção**
- Contratar um PSP e usar **cobrança dinâmica** (`cob`/`cobv`, API Pix do Bacen): valor travado e `txid` registrado no PSP.
- Webhook com **mTLS** e allowlist de IPs do PSP, além do HMAC já existente.
- Regra operacional: confirmar **só pelo extrato ou API do banco, nunca por imagem de comprovante**.
- Conciliação diária automática (cobranças × extrato).
- Fluxo de devolução (`REFUND_PENDING`) com prazo e responsável.

### A5 — Implantação
- O backend usa H2 em memória: **todo pedido se perde ao reiniciar**.
- O front roda no servidor de desenvolvimento do Vite.
- Não há TLS.

O que fazer está detalhado na seção 3.

### M1 — Sessão
- O token fica em `localStorage`: qualquer XSS o lê.
- Não há CSP.
- Não há revogação: logout, bloqueio de senha ou troca de papel não invalidam um token já emitido (o bloqueio de usuário invalida, pela checagem `isEnabled`).

**Correção**
- CSP estrita no servidor do front (`default-src 'self'; img-src 'self' https:; connect-src https://api.seudominio; frame-ancestors 'none'`).
- Access token curto (5–15 min) e refresh token em cookie `HttpOnly; Secure; SameSite=Strict` com rotação. Se usar cookie, reabilitar proteção CSRF.
- `tokenVersion` no usuário (incrementado em logout, troca de senha ou de papel) e validado no `JwtAuthFilter`.

### M2 — CORS aberto
Teste feito: `Origin: https://site-malicioso.example` foi refletido em `Access-Control-Allow-Origin`.

**Correção:** `allowedOrigins` = domínio exato do front em produção, vindo de configuração.

### M3 / M4 — Swagger e H2 expostos
- `/v3/api-docs` e `/swagger-ui` respondem 200 pela rede.
- O console H2 está ligado, mas o H2 recusou o acesso remoto ("remote connections are disabled").

**Correção (perfil prod)**
- `springdoc.api-docs.enabled=false` e `springdoc.swagger-ui.enabled=false`, ou deixar só atrás de autenticação de admin.
- `spring.h2.console.enabled=false` e retirar o H2 do artefato de produção.
- Remover `web.ignoring()` dessas rotas.

### M5 — Migrações e PostgreSQL
`V1__init.sql` usa `BIGINT AUTO_INCREMENT`, que não existe no PostgreSQL.

**Correção:** migrar para `BIGINT GENERATED BY DEFAULT AS IDENTITY` (aceito por H2 2.x e PostgreSQL) numa base nova, ou separar por fornecedor (`db/migration/{vendor}`). Validar com Testcontainers no CI.

### M6 — Auditoria
Não há registro de quem mudou o status de um pedido, bloqueou ou promoveu usuário, ou confirmou um Pix manualmente. Isso é necessário para investigar fraude, para a responsabilização prevista na LGPD (art. 6º, X; art. 37) e para guardar registros de acesso (Marco Civil, art. 15).

**Correção**
- Tabela `audit_log` (ator, ação, alvo, antes/depois, IP, data) gravada nos serviços de admin e de pagamento.
- Logs de acesso guardados por pelo menos **6 meses**, com acesso restrito.

### M7 — Contas
- O back-end aceita `123456`; a regra "letras e números" existe só no front.
- Não há verificação de email nem "esqueci minha senha".
- Não há MFA para admin.

**Correção**
- Política de senha no back-end, com checagem contra senhas vazadas (ex.: k-anonymity do HIBP).
- Verificação de email.
- Recuperação de senha com token de uso único e curto.
- **TOTP obrigatório para ADMIN.**
- Área `/admin` atrás de VPN ou allowlist de IP, se possível.

### M8 — Webhook e concorrência de pagamento
Além do mTLS (A4), aplicar o lock de C2 em `Payment` para que webhook e job não gravem estados conflitantes.

### B1 / B2
- `ProductRequest`: `@DecimalMax("99999999.99")`, `@Size` em `description` e `imageUrl` aceitando só `https://`.
- Front: cabeçalhos `Strict-Transport-Security`, `Content-Security-Policy`, `Referrer-Policy: strict-origin-when-cross-origin` e `Permissions-Policy` no servidor estático.

---

## 3. Checklist técnico para produção

**Infraestrutura**
- [ ] Domínio próprio e **HTTPS** em tudo (Let's Encrypt ou certificado do provedor), com HSTS e redirect de HTTP para HTTPS.
- [ ] Front: `npm run build` servido por nginx/CDN com os cabeçalhos de B2 e CSP de M1.
- [ ] API atrás de proxy reverso; `server.forward-headers-strategy=framework`; a porta 8080 não fica exposta.
- [ ] **PostgreSQL gerenciado** com criptografia em repouso, backups automáticos, restauração testada e usuário de banco com privilégio mínimo (M5).
- [ ] Perfil `prod` separado: H2, Swagger e devtools desligados, CORS restrito, segredos obrigatórios (C1, M2–M4).
- [ ] Segredos em cofre e rotação documentada.
- [ ] WAF/rate limit na borda (A1).
- [x] **Rate limit por IP do nginx revisto** (2026-11-07). Era `zone=api 20r/s burst=40`: no teste de carga
  (`load-tests/prod-like`, `edge.js`), um IP recebia 429 a partir de 30 req/s (29% das requisições), e 66% com 60 req/s.
  Clientes atrás do mesmo IP (CGNAT de operadora móvel, empresa) somam as requisições. Agora é `100r/s burst=200`; o
  login continua com `10r/m`. Ataque distribuído continua sendo papel de WAF/CDN.
- [x] **Limite de memória do container da API** (`mem_limit: ${API_MEM_LIMIT:-1536m}` em
  `deploy/docker-compose.prod.yml`). Sem ele, a JVM (`MaxRAMPercentage=75`) dimensionava o heap pela RAM da máquina.
  O pico no teste foi de 555 MiB.
- [x] **Cabeçalhos de segurança sem duplicidade:** a API e o nginx enviavam os dois o HSTS, o CSP, o
  X-Frame-Options, o X-Content-Type-Options e o Referrer-Policy, este com valores diferentes. Agora `proxy-api.inc`
  esconde os da API, e a borda é a fonte única em produção.

**Aplicação**
- [ ] Corrigir C1, C2, A1, A2 antes do primeiro cliente real.
- [ ] Atualizar dependências (A3) e manter Dependabot, CodeQL e scanner de CVE no CI.
- [ ] Pix via PSP com cobrança dinâmica e webhook mTLS (A4); manter a confirmação manual só como contingência.
- [ ] Sessão: CSP, token curto e revogação (M1).
- [x] **Corrida em `POST /api/cart/items` corrigida** (2026-11-07). Duas requisições simultâneas do mesmo usuário e
  produto davam 500 (`duplicate key cart_items_user_id_product_id_key`), achado no teste de carga. Agora `addItem` trava
  a linha do usuário (`SELECT … FOR UPDATE`) antes de "procura, senão insere", e as quantidades se somam. Além disso,
  qualquer violação de restrição do banco responde **409** em vez de 500, o que cobre a mesma corrida no cadastro com
  email repetido. Cobertura: `ConcurrencyIntegrationTest`, que reproduzia o erro em 7 de 8 threads antes da correção.
- [x] **Deadlock entre checkout e cancelamento corrigido** (2026-11-07, só aparecia no PostgreSQL sob carga). O
  checkout baixava o estoque na ordem do carrinho e o cancelamento devolvia na ordem do pedido: [A, B] × [B, A] →
  `deadlock detected` → 500. Agora os dois seguem a ordem de id do produto, e vítimas de trava respondem 409.
  Cobertura: `ConcurrencyIntegrationTest` (reproduzia no H2), `StockServiceTest` e `OrderServiceTest`. Na repetição
  do teste de carga: 0 erros 5xx.
- [ ] MFA para admin, verificação de email, recuperação de senha (M7).
- [ ] Auditoria e retenção de logs (M6).

**Operação**
- [ ] Logs estruturados sem senhas e sem dados pessoais desnecessários; métricas; alertas para picos de 401/429/5xx, webhooks recusados e `REFUND_PENDING`. Em dev já há traces/métricas/logs com OpenTelemetry, dashboards e 12 alertas, incluindo força bruta, varredura 401/403, rate limit e webhook Pix recusado (ADR-022, `observability-stack/`); notificações opcionais no Telegram, com o token fora do Git (`.env`) e validado no start; falta um backend de telemetria **autenticado** para produção (a stack local é aberta a quem acessa `127.0.0.1`).
- [ ] Monitoramento de disponibilidade.
- [ ] **Plano de resposta a incidentes** (quem decide, como comunicar; ver LGPD art. 48).
- [ ] Pentest externo antes do lançamento e após mudanças grandes.

---

## 4. Conformidade legal (Brasil) — o que falta

**LGPD (Lei 13.709/2018).** São tratados nome, email, telefone, endereço e histórico de compras.
- [ ] **Política de privacidade** acessível: finalidades, bases legais, compartilhamento (hospedagem, PSP, transportadora), retenção, direitos e contato (art. 9º).
  - Bases legais típicas: execução de contrato (art. 7º, V), obrigação legal fiscal (art. 7º, II) e, para marketing, consentimento (art. 7º, I).
- [ ] **Direitos do titular** (art. 18): confirmação, acesso, correção, portabilidade e eliminação. Hoje não há "baixar meus dados", "excluir minha conta" nem edição de perfil.
  - Exclusão: anonimizar dados pessoais e manter só o que a lei fiscal exige.
- [ ] **Canal do encarregado (DPO)** (art. 41). Agente de pequeno porte pode ser dispensado de indicar encarregado, mas precisa manter canal de comunicação (Resolução CD/ANPD nº 2/2022).
- [ ] **Registro das operações de tratamento** (art. 37) e política de retenção e descarte.
- [ ] **Segurança** (art. 46): os itens das seções 2 e 3.
- [ ] **Incidentes** (art. 48): comunicar ANPD e titulares em até **3 dias úteis** quando houver risco ou dano relevante (Resolução CD/ANPD nº 15/2024). O plano de resposta precisa prever isso.
- [ ] Contratos com operadores (hospedagem, PSP, email) com cláusulas de proteção de dados. Se a nuvem estiver fora do Brasil, base para **transferência internacional** (art. 33; Resolução CD/ANPD nº 19/2024).
- [ ] Cookies e armazenamento local: hoje só o `localStorage` de sessão, que é essencial. Ao adicionar analytics ou marketing, pedir consentimento antes.

**Código de Defesa do Consumidor (Lei 8.078/1990) e Decreto 7.962/2013 (comércio eletrônico)**
- [ ] Mostrar em local de destaque **razão social, CNPJ, endereço físico e eletrônico** e contato (decreto, art. 2º).
- [ ] Informações claras de produto, preço total com frete e tributos, prazo de entrega e condições. Hoje não há frete nem prazo.
- [ ] **Resumo do contrato antes da compra** e **confirmação imediata** do recebimento do pedido (art. 4º). Recomendado: email de confirmação.
- [ ] **Direito de arrependimento em 7 dias** (CDC, art. 49; decreto, art. 5º), pelo mesmo meio da compra e com devolução do valor. **Hoje o cliente não consegue cancelar o pedido**: é preciso um fluxo de cancelamento/arrependimento com estorno do Pix.
- [ ] Serviço de atendimento eletrônico para dúvidas, reclamações e cancelamento.
- [ ] **Termos de uso** e política de trocas e devoluções.

**Marco Civil da Internet (Lei 12.965/2014)**
- [ ] Guardar os **registros de acesso à aplicação por 6 meses**, em sigilo e com segurança (art. 15), para quem explora o serviço com fins econômicos (ver M6).

**Fiscal**
- [ ] Emitir **NF-e/NFC-e** em cada venda, conforme o regime tributário e a UF (integração com emissor, ex.: API de NF-e), e guardar os documentos pelo prazo legal. Combinar com o contador.

**Pix (Regulamento Pix — Resolução BCB nº 1/2020)**
- [ ] Receber em **conta de pessoa jurídica** com chave do CNPJ; o nome em `PIX_MERCHANT_NAME` deve corresponder ao titular.
- [ ] Devoluções pelo próprio Pix e procedimento para o **MED** (Mecanismo Especial de Devolução) em caso de fraude.

**Acessibilidade (Lei 13.146/2015, art. 63)**
- [ ] Site acessível. Já há labels e `aria-*` nos formulários; falta uma auditoria WCAG 2.1 AA (contraste, teclado, leitor de tela).

---

## 5. Ordem sugerida

1. **Bloqueadores (antes de qualquer cliente real):** C1, C2, A1, A2, A5 (TLS + PostgreSQL + perfil prod), M2–M5.
2. **Antes de cobrar de verdade:** A4 (PSP com Pix dinâmico + mTLS), M6, M7, política de privacidade, termos, dados da empresa, arrependimento com estorno, NF-e.
3. **Antes de crescer:** A3 contínuo, M1, pentest externo, direitos do titular automatizados, auditoria de acessibilidade.

---

## 6. Revisão 2 — 2026-11-06

Feita sobre a `main` já com as correções da revisão 1 (`6c30cc6`), para achar o que sobrou ou surgiu depois.

**Método**
- Dependências: as 88 de runtime consultadas no banco OSV; `npm audit` no front.
- Testes dinâmicos contra a API e o Vite rodando em `dev`, inclusive pela rede local.
- Revisão do código.

### Achados

| # | Severidade | Achado | Evidência (antes) | Correção | Teste |
|---|------------|--------|-------------------|----------|-------|
| R1 | Alta | Dependências com CVEs corrigidos: Jackson 2.21.4 (DoS no parsing de JSON, rotas públicas), Tomcat 10.1.55 (7 CVEs; 2 de autorização genérica), PostgreSQL JDBC 42.7.11, log4j-api 2.24.3, commons-lang3 3.17.0. O Dependabot não via porque vêm do BOM do Spring Boot | OSV: 21 vulnerabilidades sem justificativa | Versões sobrescritas no `pom.xml` (Tomcat 10.1.61, Jackson 2.21.8, PostgreSQL 42.7.14, log4j 2.25.5, commons-lang3 3.18.0); `scripts/check-dependency-cves.py` no CI falha com CVE sem justificativa; `npm audit` no CI do front | OSV depois: **0** sem justificativa; `test_check_dependency_cves.py` (4 testes) |
| R2 | Alta | Dev entregava admin a qualquer um na rede: segredo JWT de dev público e versão de sessão adivinhável (`ver=1`); senha `admin123` pública. `dev` é o perfil padrão | Token forjado pela rede → **200** em `/api/admin/users` | JWT de dev = `${random.value}` a cada start; senha do admin gerada a cada start e exibida no log (ou `ADMIN_PASSWORD`) | `SecurityReview2IntegrationTest.devJwtSecretIsNotPublic`, `AdminSeederTest` |
| R3 | Média | O bloqueio por tentativas revelava quem tem conta: 6ª tentativa → 429 só para email cadastrado | `400×5, 429` × `400×6` | Mesmo bloqueio (5 falhas / 15 min) para emails inexistentes, em memória | `lockoutDoesNotRevealAccounts`, `AuthServiceTest` (R3) |
| R4 | Média | Reautenticação sem limite (`/api/account/delete`, `/mfa/disable`): token roubado permitia descobrir a senha | 30 senhas erradas sem bloqueio; no código antigo a conta **foi excluída** após 5 erros | Falhas na reautenticação contam para o bloqueio do login; conta bloqueada → 429 | `reauthenticationIsRateLimited`, `AccountServiceTest` (R4) |
| R5 | Média | Corpo sem limite: o webhook público lia 20 MB na memória antes de recusar a assinatura (DoS com a API exposta sem nginx) | 20 MB aceitos | `RequestSizeLimitFilter`: > 64 KB → 413 (Content-Length ou leitura chunked) | `requestBodyIsLimited`, `RequestSizeLimitFilterTest` |
| R6 | Média | 404/405/415/`?sort=` inválido viravam 500 com stack trace ERROR; `GET /api/products?sort=x` é anônimo e sem rate limit → inundação de log | 4 de 5 casos → 500 | Handlers específicos no `GlobalExceptionHandler` (404, 405 + `Allow`, 415, 400) | `commonErrorsAreNotServerErrors` (verifica também que não há "Unhandled exception" no log) |

Os 5 testes de `SecurityReview2IntegrationTest` foram executados contra o código anterior e **falharam todos**
(um por achado); com as correções, passam.

### Testado e sem problema
- Overflow de `int` na quantidade do carrinho: recusado pela checagem de estoque antes da soma.
- Vite pela rede: leitura de arquivos fora do projeto → 403, inclusive com os bypasses `?raw??`. `/.env` era o fallback do SPA.
- `X-Forwarded-For` forjado em dev: ignorado (o rate limit usa o IP real).
- Front: sem `dangerouslySetInnerHTML`, `eval` ou redirecionamento aberto; `npm audit` = 0.

### Aceito / pendente
- **Webhook de dev:** o segredo continua o de exemplo, porque o `simulate-pix-payment.sh` precisa dele. Em dev, alguém na rede pode marcar pedidos de teste como pagos. Em `prod` o segredo é obrigatório e forte.
- **CVEs do spring-webmvc 6.2.x** (SSE com fragmentos, `XsltView`): recursos não usados e correção só no Spring 7. Ficam na `scripts/cve-allowlist.txt` com o motivo.
- **Bloqueio de email inexistente em memória:** vale por instância. Com várias réplicas, use um store compartilhado ou a borda (nginx/WAF).
- **Continuam da revisão 1:**
  - troca e recuperação de senha (dependem de SMTP);
  - token em `localStorage`;
  - bloqueio de conta por terceiros (15 min);
  - mensagem "email já em uso" no cadastro;
  - rate limit por IPv6 /128;
  - `server_name _` no nginx;
  - premissa de que a API nunca fica exposta sem o nginx.

---

## 7. Observabilidade — 2026-11-06, atualizado em 2026-11-07 (ADR-022, ADR-023)

A especificação de observabilidade foi implementada com estes controles. Os itens marcados com ✅ estão cobertos
por teste ou foram verificados com a stack rodando.

| Risco | Controle | Verificação |
|---|---|---|
| Actuator expondo detalhes (banco, disco) e métricas (rotas, volumes, versões) | `health` sem detalhes para anônimos; `metrics`/`prometheus` só ADMIN; `env`, `beans`, `heapdump`, `loggers` não expostos; nginx só repassa `/api/` | ✅ `ActuatorSecurityIntegrationTest` (anônimo 401, cliente 403, admin 200, sensíveis 404) |
| Agente/imagens `latest` (cadeia de suprimentos) | Versões fixas; agente via Maven Central com `--strict-checksums` | ✅ download verificado em `run-backend.sh` |
| Grafana anônimo Admin e stack aberta na rede | Portas presas a `127.0.0.1` | ✅ `ss -ltn` mostra só `127.0.0.1` |
| `traceparent` vazando para terceiros | Propagação só para a API da aplicação, regex com âncora | ✅ `config.test.ts`, `instrumentation.test.ts` |
| CORS liberando cabeçalhos demais | Só `traceparent`/`tracestate` foram adicionados; cabeçalho desconhecido continua → 403 | ✅ `CorsPreflightTest` |
| Token do bot do Telegram vazado (controle do bot) | Só no `.env` (ignorado pelo Git, permissão 600); o assistente lê o token sem eco e o passa ao `curl` pela entrada padrão, nunca na linha de comando; a API do Grafana devolve `[REDACTED]` | ✅ `TelegramTest.test_no_secret_is_versioned` (procura tokens com cara de real); conferido antes do commit do PR #26 |
| Valor do `.env` injetando conteúdo no contact point (`sed`/JSON) | `entrypoint.sh` aceita só `^[0-9]+:[A-Za-z0-9_-]+$` (token) e `^-?[0-9]+$` (chat); fora disso o Grafana não sobe | ✅ `test_entrypoint_rejects_invalid_values` (10 casos, com `/`, `&`, aspas, espaço) |
| Dados pessoais saindo para o Telegram (terceiro) | Mensagens só com nome do alerta, métrica e link local; annotations sem dados de clientes | Regra documentada no guia, seção 4.8 |
| Teste de estresse derrubando um ambiente | `stress` para sozinho com > 20% de erro; README proíbe rodar contra produção sem combinar | `load-tests/README.md` |
| ⏳ Dados pessoais e a senha de admin de dev nos logs enviados ao Loki | Stack só para dev; antes de produção, revisar o que é logado (LGPD) e usar backend autenticado | Documentado em `observability-stack/README.md` |

---

## 8. Bot de administração no Telegram — 2026-11-07 (ADR-024)

O bot dá acesso de admin pelo Telegram. Controles:

| Risco | Controle | Verificação |
|---|---|---|
| Qualquer pessoa achar o bot e mandar comandos | Lista fechada `chatId:email`; chat fora dela é ignorado (só `/start` e `/id` respondem o próprio chat ID, para a configuração) | ✅ `TelegramAdminBotTest.unknownChat` |
| Bot num grupo: outro membro clica nos botões | Só conversa privada (`chat.id == from.id`) | ✅ `groupIgnored` |
| Admin demitido/bloqueado continuar usando o chat | A cada mensagem, a conta precisa ser ADMIN, ativa, não anonimizada e com MFA (se exigido); os avisos também só vão para quem passa nessa regra | ✅ `adminNoLongerAllowed`, `TelegramOrderNotifierTest.newOrder` |
| Clique acidental ou mensagem antiga executando ação | Toda mudança pede confirmação (`?` pergunta, `!` executa); backlog descartado no start | ✅ `confirmationFirst`, `TelegramBotPollerTest.pollOnce` |
| Botão forjado (callback_data alterado) | Ações só via serviços do painel, que revalidam tudo; dados desconhecidos → "Comando inválido" | ✅ `forgedCallbacks` |
| Ação sem autor na auditoria | Admin no `SecurityContext` durante o comando; contexto limpo depois | ✅ `securityContext`, `TelegramBotIntegrationTest` (ator = admin) |
| Injeção de HTML/links na mensagem (nome de produto, motivo da devolução) | `esc()` em todo texto vindo do banco ou do cliente | ✅ `showOrder`, `showOrderWithReturn` |
| Token vazado em log (a URL da Bot API contém o token) | Mensagens de erro passam por `redact`; `toString` das propriedades sem o token; arquivo `.env.telegram-admin-bot` com permissão 600 e ignorado pelo Git | ✅ `networkErrorRedactsToken`, `toStringHidesToken`, `test_happy_path_writes_private_env_file` |
| Configuração errada deixando o bot meio ligado | Token/admins inválidos impedem o startup, sem mostrar o token | ✅ `invalidConfigurationFailsFast` |
| Dados pessoais no Telegram (terceiro, LGPD) | Mínimo necessário: endereço só cidade/UF; telefone só no detalhe do usuário | Documentado no ADR-024 |
| ⏳ Conta do Telegram roubada vira admin | Recomendar verificação em duas etapas no Telegram; bloquear a conta no painel corta o acesso na hora | Procedimento no README |

