# ShopEasy — Revisão de segurança e prontidão para produção

Data: 2026-11-05 · Escopo: `shopping-delivery/shopping-delivery` (API) e `shopping-delivery-frontend` (SPA).
**Revisão 2: 2026-11-06** (sobre o código já corrigido) — ver [seção 6](#6-revisão-2--2026-11-06).
**Revisão 3: 2026-11-08/09** (pentest de todo o monorepo, foco em produção) — ver [seção 9](#9-revisão-3--2026-11-0809).
**Revisão 4: 2026-11-09** (pentest sobre as correções da revisão 3) — ver [seção 10](#10-revisão-4--2026-11-09).
**Revisão 5: 2026-11-09** (pentest sobre a revisão 4) — ver [seção 11](#11-revisão-5--2026-11-09).

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
| Telegram aberto (celular desbloqueado, sessão esquecida) vira admin sem o 2º fator | Revisão 3, M2: admin com MFA abre a sessão com `/entrar 123456` (TOTP); vale 12 h e cai com logout/troca de senha/bloqueio no painel; códigos errados contam para o bloqueio do login | ✅ `TelegramSessionsTest`, `TelegramAdminBotTest` (M2) |
| ⏳ Conta do Telegram roubada vira admin | Agora exige também o código do app autenticador; recomendar verificação em duas etapas no Telegram; bloquear a conta no painel corta o acesso na hora | Procedimento no README |

---

## 9. Revisão 3 — 2026-11-08/09

Pentest de todo o monorepo, feito sobre a `main` com o bot do Telegram (`7b9f348`), pensando no ambiente de produção.

**Método**
- Leitura de código e configuração: API, front, nginx, Docker, CI, scripts, bot e os outros projetos do repositório.
- Ataques dinâmicos contra a API rodando, com contas descartáveis: MFA, bloqueio, carrinho/checkout, rede local.
- `npm audit` e OSV (103 dependências do backend): 0 vulnerabilidades sem justificativa.
- Busca de segredos no histórico do Git: nada novo.

Os testes de `SecurityReview3IntegrationTest` repetem cada ataque confirmado contra a aplicação inteira.

### Achados e correções

| # | Severidade | Achado | Evidência (antes) | Correção | Teste |
|---|------------|--------|-------------------|----------|-------|
| A1 | Alta | **Token roubado virava sequestro permanente da conta.** `/api/account/mfa/setup` e `/enable` aceitavam só o JWT: o atacante ativava o MFA com o app **dele** e o dono, sem o código, nunca mais entrava (não há recuperação de conta) | Com o token de uma conta de teste: setup **200**, enable **200**; login do dono → `400 MFA code required` | `setup` exige a senha (mesma reautenticação do disable/exclusão: falha conta para o bloqueio, bloqueado → 429). Sem o segredo devolvido no setup, o enable não tem como ser concluído por outra pessoa. Auditoria `MFA_SETUP_STARTED` | `stolenTokenCannotEnrollMfa`, `AccountServiceTest.mfaSetup_requiresPassword`, `AccountControllerTest.mfa`, front `privacy-security.test.tsx` |
| A2 | Alta | **Qualquer pessoa trancava qualquer conta, inclusive a do admin.** O bloqueio (5 falhas → 15 min) era da conta inteira; 1 tentativa a cada 3 min mantinha o dono fora para sempre, abaixo dos rate limits do nginx e da API | 5 senhas erradas de terceiro → a senha **certa** recebia `429 Retry-After: 900` (também em `admin@shopeasy.com`) | `LoginAttemptService`: bloqueio por **email + IP**, em memória (mesma regra para email inexistente, R3). O dono, de outro IP, entra normalmente. Falhas somadas de vários IPs (≥ 20 na janela) geram `LOGIN_ATTACK_SUSPECTED` na auditoria, sem trancar o dono. Colunas `failed_login_attempts`/`locked_until` removidas (V6) | `attackerCannotLockOwnerOut`, `AuthServiceTest` (A2), `LoginAttemptServiceTest` |
| A3 | Alta | **Uma conta esgotava o estoque da loja.** O checkout reserva o estoque enquanto o Pix não é pago (30 min), sem limite de pedidos pendentes nem de unidades: um script comprava tudo, deixava expirar e repetia | Por análise (o único teto era o próprio estoque) | Máximo de **10 unidades por produto** (DTO + soma no carrinho + checkout), **20 produtos** no carrinho e **2 pedidos aguardando Pix** por conta; o checkout trava o usuário (dois checkouts simultâneos não furam o limite). Front limita os botões de quantidade | `oneAccountCannotHoardStock`, `OrderServiceTest` (A3), `CartServiceTest` (A3), `shop.test.tsx`, `cart-checkout.test.tsx` |
| A4 | Alta (dev) | **Console H2 e Swagger abertos para a rede local.** A API de dev escuta em todas as interfaces (o front é testado de outros aparelhos); o console H2 executa SQL e, via `CREATE ALIAS`, código Java no servidor | `GET http://<ip-da-rede>:8080/h2-console/` → página do console; `/v3/api-docs` → 200 | Cadeia de segurança própria para as ferramentas de dev: só respondem para loopback (`127.0.0.1`/`::1`); pela rede → 403. Em prod continuam desligadas e o nginx devolve 404 | `devToolsOnlyFromLocalhost`, `SecurityConfigTest` |
| C1 | Crítica (outro projeto) | **`api.gestor.financeiro`: token forjável e admin de exemplo em produção.** `security.jwt.secret=${JWT_SECRET:secret-key}` (padrão público); banco `postgres/postgres` fixo e publicado na porta 5432; `data.sql` com usuários de exemplo (inclusive ADMIN) rodando em produção (`sql.init.mode=always`); imagem `openjdk:17` descontinuada | Por análise | Sem valor padrão em produção; `JwtTokenService` recusa segredo ausente, curto (< 32) ou já público; dev usa segredo aleatório. Credenciais do banco vindas do ambiente; compose sem porta do banco e com `.env` (ignorado pelo Git). Usuários de demonstração movidos para `data-demo.sql` (só dev e test). Imagens Temurin fixadas por digest | `JwtTokenServiceTest` |
| M1 | Média | **Sem troca de senha.** Senha vazada nunca podia ser trocada; a `ADMIN_PASSWORD` do `.env.prod` virava senha permanente | — | `POST /api/account/password`: exige a senha atual (e o MFA, se ativo), mesma política do cadastro, revoga todas as sessões e devolve token novo; auditoria `PASSWORD_CHANGED`. Tela em **Minha conta → Segurança** | `passwordChange`, `AccountServiceTest` (M1), `AccountControllerTest.changePassword`, `privacy-security.test.tsx` |
| M2 | Média | **Bot do Telegram dispensava o 2º fator do admin** | Por análise | Sessão do bot aberta com `/entrar <código TOTP>` (ver seção 8) | `TelegramSessionsTest`, `TelegramAdminBotTest` (M2) |
| M4 | Média | **Imagens por tag, uma fora de suporte.** `nginx-unprivileged:1.27` (linha sem correções), `postgres`, `temurin` e `node` por tag (pode ser republicada) | — | nginx **1.30** (estável); todas as imagens de produção por **digest**; Dependabot cobre também o compose; Maven com `-C` (checksum obrigatório) no build | `check-pinning.py` no CI + `test_check_pinning.py` |
| M5 | Média | **Uma rede Docker para tudo:** o nginx (exposto) alcançava o PostgreSQL, e o banco tinha saída para a internet | — | Redes `edge` (nginx ↔ API) e `data` (API ↔ banco, `internal: true`) | `docker compose config`; conferido com a pilha montada |
| M6 | Média | **Sem backup do banco** (pedidos, dados fiscais, LGPD) | — | `deploy/backup-db.sh` (pg_dump cifrado AES-256/PBKDF2, conferido com `pg_restore --list`, retenção configurável, pasta 700/arquivos 600) e `deploy/restore-db.sh` (exige digitar o nome do banco). Cron e cópia para fora do servidor documentados | `test_backup_db.py` (5 testes: cifrado, restaurável, falha não deixa arquivo, retenção, senha errada) |
| M7 | Média | **CI com permissões padrão e actions por tag** | — | `permissions: contents: read` no workflow; actions fixadas por SHA (Dependabot atualiza) | `check-pinning.py` no CI |
| B2 | Baixa | Limite de 72 da senha contava caracteres; o BCrypt corta em 72 **bytes** (acentos ocupam 2+) | Por análise | `@BcryptSafe` no cadastro e na troca de senha; o front avisa antes | `register_passwordOver72Bytes_returns400`, `validation.test.ts` |
| B4 | Baixa | Webhook Pix podia ficar sem allowlist de IPs em produção | — | `ProductionConfigValidator`: webhook ligado exige `PIX_WEBHOOK_ALLOWED_IPS`; nginx com `limit_req` próprio no webhook | `ProductionConfigValidatorTest.webhookAllowlist` |
| B5 | Baixa | `/api/products` (público) aceitava `size=2000` | `size` 2000 na resposta | `spring.data.web.pageable.max-page-size: 100` | `pageSizeCapped` |
| B6 | Baixa | `forward-headers-strategy: framework` aceitava `X-Forwarded-For` de qualquer origem: com a API exposta por outro caminho, o IP do bloqueio/rate limit/auditoria seria forjável | Por análise | `native` (RemoteIpValve): só de IPs internos (rede do compose) | Documentado no `application-prod.yml` |
| B7 | Baixa | TLS sem lista de cifras e com session tickets | — | Perfil *intermediate* da Mozilla, `ssl_session_tickets off` | Conferido: cifra fraca (`AES128-SHA`) → handshake recusado |
| B8 | Baixa | Container do nginx sem o endurecimento da API; banco sem limite de memória | — | nginx `read_only`, `tmpfs /tmp`, `cap_drop: ALL`, limite de memória; banco com `mem_limit` | nginx 1.30 sobe e serve assim (conferido) |
| B9 | Baixa | Rotas autenticadas pesadas só com o limite geral (100 req/s) | — | nginx: `/api/account/data-export` a 6/min por IP | `nginx -t` |
| — | Baixa (outro projeto) | `web-api`/`web-security`: senha do MySQL (`12345`) no repositório | — | Credenciais via `DB_USERNAME`/`DB_PASSWORD` | build do CI |

### Testado e sem problema
- IDOR: todas as consultas de pedido, endereço e carrinho filtram pelo dono.
- Preço e estoque calculados no servidor. Campos extras no cadastro (`role`, `active`) são ignorados.
- Webhook: HMAC com comparação em tempo constante e janela de 5 min.
- Actuator: `/actuator/env` e `/actuator/prometheus` → 401 sem login.
- nginx sobrescreve `X-Forwarded-For`. CSP rígida.

### Aceito / pendente
- **Token no `localStorage` (M3):** qualquer XSS rouba a sessão. A CSP rígida reduz o risco; o token dura 2 h e é revogável. A mudança para cookie `HttpOnly` + CSRF é grande (front e back) e fica planejada.
- **Recuperação de senha por e-mail e códigos de recuperação do MFA:** dependem de um provedor de e-mail (SMTP). Até lá, quem perde o celular depende do suporte, que precisa desativar o MFA direto no banco.
- **"Email já em uso" no cadastro (B3) e respostas que confirmam a senha** ("Account is blocked", "MFA code required", B1): a correção completa é a verificação de e-mail. Mitigado pelos rate limits.
- **Ataque distribuído contra uma conta:** cada IP tem 5 tentativas a cada 15 min. O evento `LOGIN_ATTACK_SUSPECTED` vai para a auditoria; com muitos IPs, o bloqueio é papel de WAF/CDN. Senha forte e MFA continuam sendo a defesa.
- **Rastreamento de tentativas em memória:** vale por instância e zera no restart, como o rate limit.
- **Rotação da chave que cifra os segredos de MFA (`APP_ENCRYPTION_KEY`):** chave única, sem versão.

### Regras para não repetir (checklist de PR)

Cada falha desta revisão virou uma regra. Na dúvida, o PR não entra. O CI já verifica as regras 7 e 8.

1. **Mudou credencial ou 2º fator → reautenticação.** Senha, MFA (ativar/desativar), e-mail, exclusão de conta: exigir a senha atual (e o MFA, se ativo) pelo `reauthenticate` do `AccountService`. Só o JWT nunca basta (A1, M1).
2. **Bloqueio nunca pela identidade da vítima sozinha.** Contar falhas por *identidade + origem* (`LoginAttemptService`); quem erra tranca a si mesmo (A2).
3. **Reservou recurso para um usuário → teto por usuário.** Estoque, cupons, horários, qualquer coisa que outro cliente deixa de poder usar enquanto está reservada (A3).
4. **Ferramenta de dev só em loopback.** Console de banco, Swagger, actuator extra: nunca pela rede, nem em dev (A4).
5. **Segredo sem valor padrão em produção** (`${X}`, nunca `${X:algo}`) e validado no start (C1; `ProductionConfigValidator`, `JwtTokenService` do gestor).
6. **Dado de exemplo nunca roda em produção:** seeds de usuários só em perfis dev/test (C1).
7. **Imagem por digest, action por SHA** (M4, M7; `check-pinning.py`).
8. **Workflow com permissão mínima** (`permissions: contents: read`; M7).
9. **Novo serviço no compose:** rede mínima (`data` é `internal`), `read_only`, `cap_drop: ALL`, `no-new-privileges`, `mem_limit` (M5, B8).
10. **Endpoint público com paginação ou corpo:** teto de tamanho (B5; `RequestSizeLimitFilter`).
11. **Cada achado de segurança ganha teste de regressão** que reproduz o ataque (`SecurityReview{2,3,4}IntegrationTest`).
12. **Teste de ataque varia o que o atacante controla:** origem (IPs diferentes, IPv6 no mesmo /64), conta, sessão,
    concorrência. Um teste que ataca de um IP só não pega um controle que conta por IP (revisão 4, R4-1/R4-2).
13. **Mudou um controle de segurança → rode os ataques de TODAS as revisões anteriores** e leia o que cada teste
    antigo deixou de cobrir com a regra nova (não basta ficar verde).
14. **Limite contra força bruta sempre tem um teto que não depende da origem** (por conta ou por sessão); bloquear o
    dono nunca é a saída — use origens confiáveis, espera ou CAPTCHA (R4-1).
15. **Ação administrativa sensível exige step-up e avisa os outros admins.** "Sensível" = cria/remove poder (papel,
    bloquear admin — R4-3) **ou move dinheiro/mercadoria** (confirmar Pix, estornar — R5-2). Antes de criar uma ação
    de admin, pergunte: *com só o token de admin roubado, o atacante ganha algo com isto?* Se sim, `StepUp`.
16. **Nunca grave uma entidade lida fora da transação** (`save` de objeto antigo desfaz mudanças feitas no meio); para
    campos de controle use `UPDATE` condicional atômico (R4-6/R4-7).
17. **Estrutura em memória com limite:** a limpeza roda no máximo de tempos em tempos, nunca a cada requisição (cheia
    de entradas válidas, virava O(n) por chamada — revisão 4).
18. **Senha:** além do formato, recusar senhas comuns, palavras óbvias com números e dados da própria pessoa
    (`PasswordStrength`); o teto de tentativas por conta é progressivo (R5-1). Senha de fixture de teste nunca é
    "senha123": use uma que passe na política (`Vagalume4821`).
19. **Identificador que aparece para humanos (e-mail, nome de usuário) só em ASCII** (ou normalizado): letras de outro
    alfabeto imitam contas reais (R5-3).
20. **Privilégio mínimo também no banco:** a API usa um usuário só de DML; migrações com o dono; superusuário só para
    criar o banco. Segredo que um container não usa não entra nele (R5-4).
21. **Dado pessoal novo nasce com prazo de retenção, exportação e exclusão** — tabela ou campo com IP, e-mail ou
    documento entra no `DataRetentionJob`, na exportação LGPD e na anonimização (R5-5).
22. **Invariantes do sistema:** "sempre existe um admin", "estoque nunca negativo" etc. são checados no serviço, não
    só na interface (R5-6).
23. **Verificar-e-depois-agir sobre estado compartilhado é corrida:** use uma operação atômica (`compute`, `UPDATE`
    condicional, trava de linha) — R4-7, R5-7.
24. **Toda imagem executada em produção ou no CI, inclusive em scripts, por digest** (`check-pinning.py` cobre
    `Dockerfile`, compose e `*IMAGE=` em scripts — R5-8).
25. **Validar a pilha de produção de verdade** (`load-tests/prod-like/prod-like.sh up` + smoke) quando mudar
    configuração de produção: a revisão 3 deixou o `prod-like` sem subir (allowlist do webhook) por duas revisões.
26. **Antes do PR, rode o JOB do CI inteiro localmente**, lido do próprio `ci.yml`:
    `python3 shopping-delivery/scripts/run-ci-job.py shopping-delivery` (e o job do front, se mexeu nele). Rodar só
    os testes deixou passar um passo quebrado ("k6 inspect") no PR da revisão 5. Valor lido por script (`sed`, `grep`)
    tem teste que usa a mesma leitura (`test_check_pinning.test_ci_reads_the_pinned_k6_image`).

---

## 10. Revisão 4 — 2026-11-09

Pentest sobre a `main` com as correções da revisão 3 (`1bdb833`). Duas das falhas altas foram **regressões que a
própria revisão 3 introduziu**: o bloqueio passou a contar por IP e os testes atacavam de um IP só. Por isso as
regras 12 e 13 acima e, desta vez, cada teste novo foi executado também contra o código antigo — os 6 testes de
`SecurityReview4IntegrationTest` **falharam todos na `main` anterior** e passam com as correções.

**Método:** ataques dinâmicos com 7 origens diferentes da mesma máquina (loopback, IPv6, rede local e pontes Docker),
leitura do código novo da revisão 3, OSV (103 dependências, 0 sem justificativa) e `npm audit` (0).

### Achados e correções

| # | Severidade | Achado | Evidência (antes) | Correção | Teste |
|---|------------|--------|-------------------|----------|-------|
| R4-1 | Alta | **Força bruta trocando de IP (regressão da A2).** Cada IP tinha 5 tentativas; com IPv6, um /64 dá 2^64 endereços | 35 senhas contra a mesma conta em 15 min com 7 origens (antes da revisão 3: 5) | IPv6 conta pelo **/64**. **Teto por conta: 20 falhas / 15 min** somando todas as origens; depois disso só tentam as **origens confiáveis** (onde a conta já entrou com sucesso, tabela `trusted_login_sources`, 90 dias, máx. 10, apagadas com a conta) — o dono em casa entra, o atacante não ganha tentativas. Email inexistente segue a mesma regra. `LOGIN_ATTACK_SUSPECTED` vai para a auditoria **e para os admins no Telegram**. Rate limit da API também por /64 | `distributedBruteForceIsBounded`, `AuthServiceTest` (R4-1), `LoginAttemptServiceTest`, `TrustedLoginSourcesTest`, `RateLimitFilterTest.ipv6CountsBySlash64` |
| R4-2 | Alta | **Token roubado testava a senha trocando de IP (regressão da R4 da revisão 2)** | 35 senhas na reautenticação com 7 origens | Reautenticação (setup/desativação do MFA, troca de senha, exclusão, `/entrar` do bot, ações de admin) conta **por conta**: 5 falhas → 15 min, de qualquer IP. Bloqueia só a reautenticação — o login do dono continua | `stolenTokenCannotBruteForceReauth`, `AuthServiceTest.reauthHelpers`, `SecurityReview2IntegrationTest` (ajustado) |
| R4-3 | Alta | **Token de admin roubado criava admin permanente** | `PATCH /api/admin/users/9 {"role":"ADMIN"}` → 200; logout do admin não desfazia | Trocar papel ou alterar conta de admin exige **senha e MFA de quem age** (`400 Password confirmation required` sem eles); todos os admins recebem alerta no Telegram. No bot, essas ações saíram (só pelo painel); o bot só bloqueia/desbloqueia clientes | `stolenAdminTokenCannotCreateAdmins`, `AdminUserServiceTest` (R4-3), `TelegramAdminBotTest.sensitiveUserChangesOnlyInPanel`, `admin.test.tsx` |
| R4-4 | Média | **Os tetos da A3 não protegiam produto de estoque baixo** | 1 conta reservou as 10 unidades do produto de estoque 10 | (a) 10 unidades por produto **somando os pedidos pendentes**; (b) um pedido leva no máximo **metade do que resta**; (c) **3 Pix abandonados em 24 h** bloqueiam novos pedidos; (d) **10 contas novas por origem por dia** (`REGISTRATIONS_PER_DAY`) | `oneAccountCannotTakeLowStockProduct`, `OrderServiceTest` (R4-4), `RegistrationLimitIntegrationTest`, `RegistrationLimiterTest` |
| R4-5 | Média | **Exclusão de conta (LGPD) bloqueável:** o e-mail anonimizado era `removed-<id>@anonymized.invalid`, previsível | Cadastro de `removed-<id>` → 201; exclusão da vítima → 409 para sempre | E-mail anonimizado com UUID; cadastro recusa o domínio reservado `.invalid` | `accountDeletionCannotBeBlocked`, `AccountServiceTest.delete` |
| R4-6 | Baixa | O `/entrar` do bot regravava a conta do admin inteira (lida no início da mensagem): podia desfazer um bloqueio feito no mesmo instante | Por análise | Só o passo TOTP, por `UPDATE` condicional (`UserRepository.claimMfaStep`) | `TelegramSessionsTest` |
| R4-7 | Baixa | O mesmo código MFA valia em dois logins simultâneos | **Confirmado no teste:** `[200, 200]` no código antigo | `UPDATE ... WHERE mfa_last_step < :passo` — só uma requisição grava | `mfaCodeRaceAcceptsOnlyOne` (duas threads, banco real), `AuthServiceTest.mfaClaimIsAtomic` |
| R4-8 | Baixa | Imagem de produto de qualquer domínio https: o dono do servidor via o IP de cada cliente | Por análise | `app.product-image-hosts` (`PRODUCT_IMAGE_HOSTS`, padrão `images.unsplash.com`) e o mesmo host no `img-src` do CSP | `AdminProductServiceTest.imageHostAllowlist` |
| — | Baixa (achado durante a correção) | Com o mapa de tentativas cheio de entradas válidas, **cada falha varria o mapa inteiro** (O(n) por requisição: CPU) — também no rate limit | O teste de memória levava 171 s | Limpeza no máximo 1 vez por minuto (`LoginAttemptService`, `RegistrationLimiter`, `RateLimitFilter`) | `LoginAttemptServiceTest.evictsStaleEntriesWhenFull` (0,8 s) |

### Aceito / pendente
- **Origem nova durante um ataque em andamento:** quem tenta entrar de um lugar onde nunca entrou (viagem, celular novo) recebe 429 até a janela de 15 min passar. É o custo de não dar tentativas ilimitadas a um atacante.
- **Muitas contas por muitas origens:** com proxies residenciais, o limite de cadastros por origem só encarece o ataque. A correção completa é a verificação de e-mail (pendente, depende de SMTP).
- **"3 Pix abandonados"** também conta pedidos sem pagamento cancelados pelo admin; é raro e a janela é de 24 h.
- **Rate limit do nginx por endereço IPv6 (/128):** a API já agrupa por /64; o nginx continua por endereço.
- **Continuam da revisão 3:** token no `localStorage`, recuperação de senha e códigos de recuperação do MFA, verificação de e-mail.

---

## 11. Revisão 5 — 2026-11-09

Pentest sobre a `main` com a revisão 4 (`b30923c`), mirando o que um token de admin roubado ainda faz, a política de
senha, privilégios do banco, CI e os dados pessoais que a própria revisão 4 criou. Cada correção tem teste que
**falha no código anterior** (conferido) e os ataques das revisões 3, 4 e 5 foram refeitos ao vivo de origens
distintas. A pilha de produção (PostgreSQL + API `prod` + nginx/TLS) foi **subida e testada** — e assim apareceu que
ela não subia desde a revisão 3 (regra 25).

### Achados e correções

| # | Severidade | Achado | Evidência (antes) | Correção | Teste |
|---|------------|--------|-------------------|----------|-------|
| R5-1 | Média | **Senhas óbvias aceitas**, e o teto de 20 falhas/15 min renovava: ~1.900 chutes/dia por conta | `password1`, `senha123`, `12345678a`, `qwerty123`, `admin123`, `shopeasy1`, `Mudar123` → 201 | `PasswordStrength`: lista de senhas comuns (SecLists, fixada por commit e sha256), palavra fraca com números em volta (inclusive `p4ssw0rd`, `S3nh@`), quase só números, nome da loja/e-mail/nome da pessoa. No cadastro, na troca de senha e na `ADMIN_PASSWORD` de produção. Teto por conta **progressivo**: cada estouro dobra o bloqueio (15, 30, 60 min… até 24 h), esquecido 24 h depois do fim do último | `predictablePasswordsAreRefused`, `PasswordStrengthTest`, `LoginAttemptServiceTest` (R5-1) |
| R5-2 | Média | **Token de admin roubado marcava o próprio pedido como pago** | `POST /payment/confirm` → 200; depois `SHIPPED` → 200: pedido enviado sem Pix | Confirmar Pix e estornar exigem **step-up** (`StepUp`: senha + MFA no painel; no bot, `/pix 12 <código novo>`) e avisam todos os admins. Mudar status já recusava enviar com Pix pendente | `stolenAdminTokenCannotConfirmPix`, `AdminOrderServiceTest`, `AdminControllersTest`, `TelegramAdminBotTest.payments`, `admin.test.tsx` |
| R5-3 | Baixa | **E-mail parecido com o do admin** (`аdmin@…` com "а" cirílico) | Cadastro 201; painel com duas contas idênticas | Cadastro só aceita e-mail ASCII (back e front) | `lookalikeEmailIsRefused`, `validation.test.ts` |
| R5-4 | Baixa | **API usava o superusuário do PostgreSQL** (falha de SQL = controle do banco e do container) | Por análise | Três usuários: superusuário só cria o banco (`deploy/db-init/01-roles.sh`), dono das tabelas só migra (`spring.flyway.user`), API só DML. Callback do Flyway tira da API o histórico de migrações. A senha do superusuário não entra no container da API. `ProductionConfigValidator` recusa usuário da API igual ao dono/`postgres`. Backup/restore com o dono | Script testado em banco novo e antigo (API: `CREATE`/`ALTER`/`DROP`/`COPY PROGRAM` → negado); pilha `prod-like` subida; `ProductionConfigValidatorTest`, `test_backup_db.py` |
| R5-5 | Baixa | **Dados pessoais sem prazo**: auditoria (IP, e-mail) para sempre; origens confiáveis de quem não volta | Por análise | `DataRetentionJob` diário: auditoria além de `AUDIT_RETENTION_DAYS` (padrão 365, mínimo 180 — Marco Civil) e origens sem login há 90 dias | `DataRetentionJobTest` |
| R5-6 | Baixa | **O último admin podia excluir a própria conta** (loja sem admin; no restart, conta recriada com a `ADMIN_PASSWORD` do `.env`) | Por análise | Exclusão recusada para o último admin ativo | `lastAdminCannotDeleteAccount`, `AccountServiceTest` |
| R5-7 | Baixa | **Corrida no limite de cadastros** (verifica, depois registra) | **Confirmado no teste:** no código anterior, com limite 2, passaram **10 de 20** cadastros simultâneos | Reserva atômica da vaga (`RegistrationLimiter.acquire`) | `concurrentRegistrationsRespectTheLimit`, `RegistrationLimiterTest` |
| R5-8 | Baixa | **Imagem do k6 por tag no CI** (roda com o repositório montado) | Por análise | Fixada por digest; `check-pinning.py` também olha `*IMAGE=` em scripts | `test_check_pinning.py` |
| — | Baixa (achado na correção) | O `prod-like` **não subia desde a revisão 3**: o webhook ligado exige `PIX_WEBHOOK_ALLOWED_IPS` (B4) e o script gerava vazio | `ProductionConfigValidator` recusava o startup | Script gera a allowlist e os três usuários do banco | pilha subida (regra 25) |
| — | Baixa (achado na correção) | A contagem de falhas **por origem nunca vencia** antes do bloqueio: um erro por dia bloqueava no 5º dia | Por análise do teste | Contagem vence 15 min depois da última falha | `LoginAttemptServiceTest.clientCountExpires` |

**Falha do próprio PR (corrigida antes do merge):** o CI do PR quebrou no passo "k6 inspect": o comentário colocado
no fim da linha `K6_IMAGE=...` do `load-tests/run.sh` (R5-8) fazia o `sed` do `ci.yml` devolver vazio. Os testes
passavam localmente porque esse passo não foi rodado. Correção: comentário na linha de cima; teste que roda o mesmo
`sed` do `ci.yml`; `scripts/run-ci-job.py` roda todos os passos de um job localmente (regra 26).

**Falso positivo descartado:** a exportação LGPD não traz a tabela de origens confiáveis, mas os mesmos IPs já saem
nos eventos de segurança (auditoria) — nenhum dado fica de fora.

### Banco já existente (atualização para a revisão 5)
O `01-roles.sh` roda sozinho só em volume novo. Num banco que já existe (tabelas do usuário antigo):
1. No `.env.prod`: `DATABASE_ADMIN_USERNAME` = o usuário antigo (ele é o superusuário do volume) e a senha dele em
   `DATABASE_ADMIN_PASSWORD`; crie `DATABASE_MIGRATION_*` e um **novo** `DATABASE_USERNAME`/`DATABASE_PASSWORD`.
2. `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d db`
3. `docker compose -f docker-compose.prod.yml --env-file .env.prod exec db bash /docker-entrypoint-initdb.d/01-roles.sh`
   (passa as tabelas para o dono e dá só DML à API; pode rodar de novo).
4. Suba a API.

### Aceito / pendente
- **Lista de senhas comuns local** (~370 entradas que passariam no formato): não cobre vazamentos novos. Uma consulta
  k-anonymity ao Have I Been Pwned exigiria saída para a internet no cadastro; fica como melhoria.
- **Retenção da auditoria em 365 dias** é um padrão técnico: o prazo final depende do jurídico (defesa em processos x
  minimização).
- **Continuam:** token no `localStorage`, verificação de e-mail, recuperação de senha e de MFA, limite IPv6 /64 no nginx.

