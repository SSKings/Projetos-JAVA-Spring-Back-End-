import { describe, expect, it } from 'vitest';
import { preflightMessages } from './dev-preflight.mjs';

describe('dev-preflight', () => {
  it('branch atualizada: só informa a branch', () => {
    expect(preflightMessages({ branch: 'main', missingFromMain: 0, behindUpstream: 0, upstream: 'origin/main' }))
      .toEqual(['[dev] branch atual: main']);
  });

  it('branch sem commits que já estão na main: avisa e diz como atualizar', () => {
    const lines = preflightMessages({ branch: 'fix/ci', missingFromMain: 3, behindUpstream: 0, upstream: '' });
    expect(lines[0]).toBe('[dev] branch atual: fix/ci');
    expect(lines[1]).toContain('não tem 3 commit(s) que já estão em origin/main');
    expect(lines[2]).toContain('git merge origin/main');
  });

  it('atrás do upstream: sugere git pull; HEAD destacado é identificado', () => {
    const lines = preflightMessages({ branch: '', missingFromMain: 0, behindUpstream: 2, upstream: 'origin/x' });
    expect(lines).toEqual([
      '[dev] branch atual: (HEAD destacado)',
      '[dev] ATENÇÃO: 2 commit(s) atrás de origin/x. Rode: git pull',
    ]);
  });
});
