# ShopEasy — Revisão de segurança e prontidão para produção

Data: 2026-11-05 · Escopo: `shopping-delivery/shopping-delivery` (API) e `shopping-delivery-frontend` (SPA).

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
| A3 | ✅ | Spring Boot 3.3.7 → **3.5.16** (Tomcat, Spring Framework 6.2 e Spring Security corrigidos), springdoc 2.8.17; Dependabot para Maven, npm, Docker e Actions | `./mvnw verify` e `npm run coverage` verdes. Migrar para o Spring Boot 4.x é o próximo passo (mudança grande) |
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

**Aplicação**
- [ ] Corrigir C1, C2, A1, A2 antes do primeiro cliente real.
- [ ] Atualizar dependências (A3) e manter Dependabot, CodeQL e scanner de CVE no CI.
- [ ] Pix via PSP com cobrança dinâmica e webhook mTLS (A4); manter a confirmação manual só como contingência.
- [ ] Sessão: CSP, token curto e revogação (M1).
- [ ] MFA para admin, verificação de email, recuperação de senha (M7).
- [ ] Auditoria e retenção de logs (M6).

**Operação**
- [ ] Logs estruturados sem senhas e sem dados pessoais desnecessários; métricas; alertas para picos de 401/429/5xx, webhooks recusados e `REFUND_PENDING`.
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
