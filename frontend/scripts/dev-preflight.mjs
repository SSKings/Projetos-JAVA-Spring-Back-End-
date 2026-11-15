#!/usr/bin/env node
/**
 * Roda antes do `npm run dev` (script "predev" do package.json).
 *
 * Por quê: o servidor de desenvolvimento serve o código da branch que está em checkout. Se essa branch
 * não tiver commits que já estão na main, o app "perde" funcionalidades sem aviso — foi assim que as
 * validações e máscaras dos formulários pareceram quebradas (ver docs/dev-workflow.md).
 *
 * Só avisa: nunca falha, não acessa a rede (usa a referência local origin/main; rode `git fetch` para
 * atualizá-la) e não faz nada fora de um repositório git.
 */
import { execFileSync } from 'node:child_process';

const git = (...args) =>
  execFileSync('git', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();

/** Monta as mensagens a partir do estado do git (função pura, testada em dev-preflight.test.mjs). */
export function preflightMessages({ branch, missingFromMain, behindUpstream, upstream }) {
  const messages = [`[dev] branch atual: ${branch || '(HEAD destacado)'}`];
  if (missingFromMain > 0) {
    messages.push(
      `[dev] ATENÇÃO: esta branch não tem ${missingFromMain} commit(s) que já estão em origin/main.`,
      '[dev] Funcionalidades já mescladas podem não aparecer. Atualize: git merge origin/main (ou troque para a main).',
    );
  }
  if (behindUpstream > 0) {
    messages.push(`[dev] ATENÇÃO: ${behindUpstream} commit(s) atrás de ${upstream}. Rode: git pull`);
  }
  return messages;
}

function count(range) {
  try {
    return Number(git('rev-list', '--count', range)) || 0;
  } catch {
    return 0; // referência inexistente (ex.: clone sem origin/main)
  }
}

function main() {
  let branch;
  try {
    branch = git('rev-parse', '--abbrev-ref', 'HEAD');
  } catch {
    return; // fora de um repositório git (ex.: build em container)
  }
  let upstream = '';
  try {
    upstream = git('rev-parse', '--abbrev-ref', '--symbolic-full-name', '@{u}');
  } catch {
    // branch sem upstream
  }
  const missingFromMain = branch === 'main' ? 0 : count('HEAD..origin/main');
  const behindUpstream = upstream ? count('HEAD..@{u}') : 0;
  for (const line of preflightMessages({ branch, missingFromMain, behindUpstream, upstream })) {
    (line.includes('ATENÇÃO') ? console.warn : console.log)(line);
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  try {
    main();
  } catch {
    // o aviso nunca pode impedir o servidor de subir
  }
}
