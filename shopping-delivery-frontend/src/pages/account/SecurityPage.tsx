import { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { ShieldCheck, ShieldAlert } from 'lucide-react';
import { disableMfa, enableMfa, setupMfa } from '../../api/account';
import QrCodeImage from '../../components/QrCodeImage';
import { useAuth } from '../../contexts/AuthContext';
import { getErrorMessage } from '../../utils/format';
import type { MfaSetup } from '../../types';

const codeInput = 'w-40 px-3 py-2 border border-gray-300 rounded-lg text-sm tracking-widest focus:outline-none focus:ring-2 focus:ring-indigo-500';
const onlyDigits = (v: string) => v.replace(/\D/g, '').slice(0, 6);

/** Verificação em duas etapas (TOTP). Obrigatória para administradores em produção. */
export default function SecurityPage() {
  const { user, updateSession } = useAuth();
  const location = useLocation();
  const [setup, setSetup] = useState<MfaSetup | null>(null);
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      await action();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const start = () => run(async () => { setSetup(await setupMfa()); setCode(''); });

  const confirm = (e: React.FormEvent) => {
    e.preventDefault();
    return run(async () => {
      updateSession(await enableMfa(code));   // o back-end revogou as sessões antigas e devolveu um token novo
      setSetup(null);
      setCode('');
      setSuccess('Verificação em duas etapas ativada. As outras sessões foram encerradas.');
    });
  };

  const disable = (e: React.FormEvent) => {
    e.preventDefault();
    return run(async () => {
      updateSession(await disableMfa(password, code));
      setPassword('');
      setCode('');
      setSuccess('Verificação em duas etapas desativada.');
    });
  };

  const adminRequired = user?.mfaSetupRequired || (location.state as { reason?: string } | null)?.reason === 'admin-mfa';

  return (
    <section className="bg-white rounded-xl border border-gray-200 p-5 space-y-4 text-sm">
      <h2 className="flex items-center gap-2 text-lg font-semibold text-gray-900">
        {user?.mfaEnabled ? <ShieldCheck size={20} className="text-green-600" /> : <ShieldAlert size={20} className="text-amber-600" />}
        Verificação em duas etapas
      </h2>
      {adminRequired && !user?.mfaEnabled && (
        <p role="note" className="bg-amber-50 text-amber-800 rounded-lg px-4 py-3">
          Administradores precisam ativar a verificação em duas etapas para acessar a área administrativa.
        </p>
      )}
      {error && <p role="alert" className="bg-red-50 text-red-700 rounded-lg px-4 py-2">{error}</p>}
      {success && <p role="status" className="bg-green-50 text-green-700 rounded-lg px-4 py-2">{success}</p>}

      {user?.mfaEnabled ? (
        <form onSubmit={disable} className="space-y-3">
          <p className="text-gray-600">Ativa. Ao entrar, pedimos o código do seu app autenticador.</p>
          <p className="text-gray-500">Para desativar, confirme sua senha e um código atual:</p>
          <div className="flex flex-wrap gap-3 items-end">
            <label className="block">
              <span className="block text-gray-600 mb-1">Senha</span>
              <input type="password" autoComplete="current-password" required value={password}
                     onChange={e => setPassword(e.target.value)}
                     className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
            </label>
            <label className="block">
              <span className="block text-gray-600 mb-1">Código</span>
              <input inputMode="numeric" autoComplete="one-time-code" required value={code}
                     onChange={e => setCode(onlyDigits(e.target.value))} className={codeInput} />
            </label>
            <button disabled={busy} className="px-4 py-2 border border-red-300 text-red-700 rounded-lg hover:bg-red-50 disabled:opacity-50">
              Desativar
            </button>
          </div>
        </form>
      ) : setup ? (
        <form onSubmit={confirm} className="space-y-3">
          <ol className="list-decimal list-inside text-gray-600 space-y-0.5">
            <li>Abra um app autenticador (Google Authenticator, Microsoft Authenticator, 1Password…).</li>
            <li>Leia o QR Code ou digite a chave abaixo.</li>
            <li>Digite o código de 6 dígitos que aparecer.</li>
          </ol>
          <QrCodeImage value={setup.otpauthUri} alt="QR Code para o app autenticador" />
          <p className="text-gray-500">Chave: <code className="font-mono text-gray-800 break-all">{setup.secret}</code></p>
          <div className="flex gap-3 items-end">
            <label className="block">
              <span className="block text-gray-600 mb-1">Código do app</span>
              <input inputMode="numeric" autoComplete="one-time-code" required autoFocus value={code}
                     onChange={e => setCode(onlyDigits(e.target.value))} className={codeInput} />
            </label>
            <button disabled={busy || code.length !== 6}
                    className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg disabled:opacity-50">
              Confirmar e ativar
            </button>
          </div>
        </form>
      ) : (
        <div className="space-y-3">
          <p className="text-gray-600">
            Proteja sua conta: além da senha, pediremos um código temporário gerado no seu celular.
          </p>
          <button onClick={start} disabled={busy} className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg disabled:opacity-50">
            Ativar verificação em duas etapas
          </button>
        </div>
      )}
    </section>
  );
}
