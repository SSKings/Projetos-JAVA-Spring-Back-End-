# Fluxo de desenvolvimento — branches e servidores locais

## O que aconteceu (2026-11-05)

As validações e máscaras dos formulários pareceram "quebrar", mas o código estava certo:

1. O trabalho de validação estava no PR #2. Para corrigir o CI de outros projetos, foi criada a branch
   `fix/ci-gestor-financeiro-web-security` a partir da `main` **de antes** do merge desse PR.
2. O checkout local foi trocado para essa branch.
3. Os servidores de desenvolvimento (Vite e `mvn spring-boot:run`) servem **o que está em checkout**. O
   Vite recarregou sozinho uma versão em que `src/utils/validation.ts` nem existia.

Nenhum teste falhou, porque os testes daquela branch eram os daquela versão. O problema era de fluxo, não de
código.

## Regras

1. **Trabalho paralelo vai para um worktree, não para o checkout em uso.** Assim, os servidores que estão
   rodando continuam na branch certa:
   ```bash
   git worktree add ../wt-fix-ci -b fix/ci origin/main   # pasta separada, mesmo repositório
   # ... trabalhe, commite e faça push lá ...
   git worktree remove ../wt-fix-ci
   ```
   **Onde:** fora do repositório e **nunca em `/tmp`**. Nesta máquina o `/tmp` é `tmpfs` e é apagado a cada
   reinício. Em 2026-11-06 um worktree em `/tmp`, com trabalho ainda sem commit, sumiu num reboot e teve de ser
   refeito. Use uma pasta em disco, por exemplo `~/Projetos-wt/<branch>`:
   ```bash
   git worktree add ~/Projetos-wt/feat-x -b feat/x origin/main
   ```
2. **Branch nova sai da `main` atualizada:** `git fetch origin` antes de `git checkout -b ... origin/main`.
3. **Trocou de branch? Reinicie a API.** O Vite recarrega o front sozinho; o Spring Boot não (migrações,
   configuração e beans novos só entram no próximo start).
4. **Antes de concluir que algo quebrou**, confira o que está rodando:
   ```bash
   git branch --show-current
   git fetch origin && git log --oneline HEAD..origin/main   # commits da main que você NÃO tem
   ```

5. **A stack de observabilidade monta arquivos da pasta de onde foi iniciada.**
   - Subiu a stack a partir de um worktree? Depois do merge, recrie-a a partir da `main`, antes de remover o
     worktree:
     ```bash
     cd shopping-delivery/observability-stack && docker compose up -d --force-recreate
     ```
   - Depois de um `git pull` que mudou `otel-config.yaml` (ou outro arquivo montado sozinho), **reinicie o
     container**. O Docker fica preso à versão antiga do arquivo, e o Compose não percebe a mudança:
     ```bash
     docker compose restart otel-collector
     ```
   - O `.env` dessa pasta (token do Telegram) não é versionado. Ao trocar de pasta, copie-o (`cp -p`) ou rode
     `./telegram-setup.sh` de novo.
6. **O bot de administração no Telegram (ADR-024) também tem um arquivo local.** O
   `shopping-delivery/.env.telegram-admin-bot` não é versionado e o `run-backend.sh` o lê da pasta de onde roda. Num
   worktree novo, copie-o (`cp -p`) ou rode `./scripts/telegram-admin-bot-setup.sh` de novo. Só **uma** API pode
   rodar com o mesmo token: duas instâncias disputam as mensagens (o Telegram responde 409).

## Aviso automático

`npm run dev` roda antes `scripts/dev-preflight.mjs` (script `predev`). Ele mostra a branch atual e **avisa**
quando ela não tem commits que já estão em `origin/main` ou quando está atrás do upstream:

```
[dev] branch atual: fix/ci-gestor-financeiro-web-security
[dev] ATENÇÃO: esta branch não tem 9 commit(s) que já estão em origin/main.
[dev] Funcionalidades já mescladas podem não aparecer. Atualize: git merge origin/main (ou troque para a main).
```

O script não acessa a rede: a comparação usa a referência local `origin/main` (rode `git fetch` para
atualizá-la). Ele nunca impede o servidor de subir e não faz nada fora de um repositório git (por exemplo,
no build do Docker). Os testes ficam em `scripts/dev-preflight.test.mjs`.

## "Uma funcionalidade sumiu" — checklist

- [ ] `git branch --show-current`: estou na branch que tem a funcionalidade?
- [ ] `git log --oneline HEAD..origin/main`: falta algum commit da `main`?
- [ ] A API foi reiniciada depois da última troca de branch?
- [ ] `npm test` passa **nesta** branch? Se passa, o código está certo e a branch está errada.

## Atualizações de dependências (Dependabot)

Decisão registrada na ADR-020.

### O que aconteceu (2026-11-05)

Em um dia o Dependabot abriu 14 PRs, um por dependência. Entre os mergeados estava o **Tailwind CSS 3 → 4**
(PR #10), uma versão major que mudou o plugin do PostCSS para outro pacote (`@tailwindcss/postcss`). Com isso:
- `npm run build` quebrou;
- o CSS do `npm run dev` parou de ser gerado;
- o CI continuou verde, porque os testes rodam com `css: false` e o job do front não fazia build.

O Tailwind voltou para `^3.4.19`.

### O que mudou para evitar

- `.github/dependabot.yml`: atualizações **minor/patch agrupadas** (um PR por ecossistema) e **majors ignoradas**.
  Major é migração e se faz num PR próprio, de propósito.
- CI do front: passo **`npm run build`** depois dos testes.

### O que o Dependabot NÃO atualiza

Estas versões são fixas de propósito e ficam fora do Dependabot. Revise de tempos em tempos, seguindo o
procedimento da seção 9 do [guia de observabilidade](instrunctions-cloud-impl-observability.md):

| O quê | Onde |
|---|---|
| Imagens da stack (Collector, Prometheus, Tempo, Loki, Grafana) | `observability-stack/docker-compose.yml` |
| Agente Java do OpenTelemetry | `pom.xml` → `otel-javaagent.version` (propriedade, não dependência) |
| Imagem do k6 | `load-tests/run.sh` → `K6_IMAGE` (o CI lê dali) |

### Antes de mergear um PR de dependência

- [ ] O CI está verde em **todos** os jobs (incluindo o build do front).
- [ ] É major? (`3.x → 4.x`). Não mergeie pelo botão: leia o guia de migração e faça um PR próprio.
- [ ] Mexe em build, CSS ou runtime (Vite, Tailwind, PostCSS, Spring Boot, imagem Docker)? Rode localmente
  `npm run build` / `./mvnw verify` e abra o app.
- [ ] Depois do merge: `git pull` na `main` e `npm ci` (o `node_modules` local não se atualiza sozinho).

### Migrar o Tailwind para a v4 (quando for a hora)

`npx @tailwindcss/upgrade` converte a configuração: `@tailwindcss/postcss` no lugar de `tailwindcss`/`autoprefixer`,
`@import "tailwindcss"` no lugar das diretivas `@tailwind` e tema em CSS. Os estilos padrão mudam (cor de borda,
largura do `ring`, escalas de `shadow`/`rounded`/`blur`), então revise as telas antes de mergear.

## Antes de abrir o PR: rode o job do CI

```bash
python3 shopping-delivery/scripts/run-ci-job.py --list               # jobs do ci.yml
python3 shopping-delivery/scripts/run-ci-job.py shopping-delivery    # todos os passos, na ordem (~4 min)
python3 shopping-delivery/scripts/run-ci-job.py shopping-delivery-frontend
```

O script lê os passos do próprio `ci.yml` (nenhum fica de fora, nem os criados depois), roda cada um no diretório
dele com `bash -eo pipefail` e encerra o que um passo deixar rodando (a API do smoke test). Precisa de Java 17, Node,
Docker e PyYAML. Rodar só `./mvnw verify` não basta: no PR da revisão 5 os 402 testes passavam e o passo "k6 inspect"
quebrou no CI.

## Segurança: checklist de PR

Antes de abrir um PR que toque autenticação, conta, carrinho/pedidos, configuração, Docker ou CI, confira as
**regras para não repetir** da revisão 3 em [`security-review.md`](security-review.md#regras-para-não-repetir-checklist-de-pr).
O CI verifica automaticamente:

- **Imagens por digest e actions por SHA:** `python3 scripts/check-pinning.py` (falha listando o que está por tag).
- **Backup do banco:** `python3 -m unittest discover -s scripts -p 'test_backup_db.py'`.
- **Regressões dos ataques já encontrados:** `SecurityReview{2,3,4,5}IntegrationTest` (a partir da 4, sempre de
  origens diferentes, inclusive IPv6 no mesmo /64).

Mudou configuração de produção (compose, `application-prod.yml`, validador, nginx)? Suba a pilha de verdade:
`./load-tests/prod-like/prod-like.sh up && ./load-tests/prod-like/prod-like.sh smoke` (e `down` no fim). Ela ficou
sem subir por duas revisões sem ninguém perceber (revisão 5).

Senha de teste: use uma que passe na política (`Vagalume4821`); "senha123" e parecidas são recusadas no cadastro.

Mudou um controle de segurança? Rode os ataques de todas as revisões e confira o que cada teste antigo deixou de
cobrir com a regra nova. Antes de dar a correção por pronta, rode o teste novo **contra o código antigo** (ele tem que
falhar): na revisão 3, testes que atacavam de um IP só deixaram passar duas regressões (revisão 4, R4-1/R4-2).

Para atualizar o digest de uma imagem à mão (o Dependabot faz isso toda semana):
`docker buildx imagetools inspect <imagem>:<tag> --format '{{json .Manifest.Digest}}'`.

