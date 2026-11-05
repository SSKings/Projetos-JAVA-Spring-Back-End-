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
2. **Branch nova sai da `main` atualizada:** `git fetch origin` antes de `git checkout -b ... origin/main`.
3. **Trocou de branch? Reinicie a API.** O Vite recarrega o front sozinho; o Spring Boot não (migrações,
   configuração e beans novos só entram no próximo start).
4. **Antes de concluir que algo quebrou**, confira o que está rodando:
   ```bash
   git branch --show-current
   git fetch origin && git log --oneline HEAD..origin/main   # commits da main que você NÃO tem
   ```

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
