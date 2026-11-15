/**
 * Copia texto para a área de transferência.
 *
 * A Clipboard API só existe em contexto seguro (https ou localhost). Acessando pela rede local
 * (http://192.168.x.x) ela é `undefined`, então caímos no `execCommand('copy')` com um textarea temporário.
 * Retorna false se nenhum dos dois funcionar — a tela então pede para copiar manualmente.
 */
export async function copyText(text: string): Promise<boolean> {
  if (window.isSecureContext && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      // permissão negada: tenta o fallback
    }
  }
  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.setAttribute('readonly', '');
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  try {
    return document.execCommand('copy');
  } catch {
    return false;
  } finally {
    document.body.removeChild(textarea);
  }
}
