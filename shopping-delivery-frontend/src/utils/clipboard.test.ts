import { afterEach, describe, expect, it, vi } from 'vitest';
import { copyText } from './clipboard';

const setSecure = (secure: boolean) => vi.stubGlobal('isSecureContext', secure);
const stubClipboard = (writeText?: (t: string) => Promise<void>) =>
  Object.defineProperty(navigator, 'clipboard', { value: writeText ? { writeText } : undefined, configurable: true });
const stubExecCommand = (impl: () => boolean) =>
  Object.defineProperty(document, 'execCommand', { value: vi.fn(impl), configurable: true });

afterEach(() => {
  vi.unstubAllGlobals();
  stubClipboard(undefined);
});

describe('copyText', () => {
  it('usa a Clipboard API em contexto seguro', async () => {
    setSecure(true);
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubClipboard(writeText);
    stubExecCommand(() => true);

    expect(await copyText('abc')).toBe(true);
    expect(writeText).toHaveBeenCalledWith('abc');
    expect(document.execCommand).not.toHaveBeenCalled();
  });

  it('em http pela rede (sem contexto seguro) usa execCommand e remove o textarea temporário', async () => {
    setSecure(false);
    stubClipboard(vi.fn());
    let copied = '';
    stubExecCommand(() => { copied = (document.activeElement as HTMLTextAreaElement | null)?.value ?? document.querySelector('textarea')!.value; return true; });

    expect(await copyText('000201pix')).toBe(true);
    expect(copied).toBe('000201pix');
    expect(document.querySelectorAll('textarea')).toHaveLength(0);
  });

  it('se a Clipboard API recusar, tenta o fallback', async () => {
    setSecure(true);
    stubClipboard(vi.fn().mockRejectedValue(new Error('denied')));
    stubExecCommand(() => true);
    expect(await copyText('x')).toBe(true);
    expect(document.execCommand).toHaveBeenCalledWith('copy');
  });

  it('retorna false quando nada funciona', async () => {
    setSecure(false);
    stubExecCommand(() => false);
    expect(await copyText('x')).toBe(false);
    stubExecCommand(() => { throw new Error('unsupported'); });
    expect(await copyText('x')).toBe(false);
    expect(document.querySelectorAll('textarea')).toHaveLength(0);
  });
});
