import { useState } from 'react';
import { MailWarning } from 'lucide-react';
import { resendVerification } from '../api/account';
import { useAuth } from '../contexts/AuthContext';
import { getErrorMessage } from '../utils/format';

/** Aviso para quem ainda não confirmou o e-mail (ADR-025): sem confirmar, não fecha pedido. */
export default function EmailVerificationBanner() {
  const { user, isAuthenticated } = useAuth();
  const [status, setStatus] = useState<{ ok: boolean; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  if (!isAuthenticated || !user || user.emailVerified) return null;

  const resend = async () => {
    setBusy(true);
    try {
      await resendVerification();
      setStatus({ ok: true, text: `Enviamos um novo link para ${user.email}.` });
    } catch (err) {
      setStatus({ ok: false, text: getErrorMessage(err) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div role="region" aria-label="Confirme seu e-mail" className="bg-amber-50 border-b border-amber-200 text-amber-900 text-sm">
      <div className="max-w-7xl mx-auto px-4 py-2.5 flex flex-wrap items-center gap-x-4 gap-y-1">
        <MailWarning size={18} className="shrink-0" />
        <span>Confirme seu e-mail para poder fechar pedidos: abra o link que enviamos para <strong>{user.email}</strong>.</span>
        <button onClick={resend} disabled={busy} className="font-medium underline disabled:opacity-50">
          Reenviar e-mail
        </button>
        {status && <span role="status" className={status.ok ? 'text-green-700' : 'text-red-700'}>{status.text}</span>}
      </div>
    </div>
  );
}
