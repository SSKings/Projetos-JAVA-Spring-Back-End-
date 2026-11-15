import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { KeyRound } from 'lucide-react';
import { resetPassword } from '../../api/auth';
import { getErrorMessage } from '../../utils/format';
import { LIMITS, validatePassword } from '../../utils/validation';

const input = 'w-full px-4 py-3 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500';

/**
 * Nova senha pelo link do e-mail (ADR-025). O token vem no fragmento (#token=…), que o navegador não manda ao servidor
 * (não fica no log do nginx); sai da barra de endereço na hora e fica só na memória da página.
 */
export default function ResetPasswordPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [token] = useState(() => new URLSearchParams(location.hash.slice(1)).get('token') ?? '');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (location.hash) navigate(location.pathname, { replace: true });
  }, [location.hash, navigate, location.pathname]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const invalid = validatePassword(password) ?? (password !== confirm ? 'As senhas não conferem.' : undefined);
    if (invalid) {
      setError(invalid);
      return;
    }
    setError('');
    setLoading(true);
    try {
      await resetPassword(token, password);
      setDone(true);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-md mx-auto px-4 py-12">
      <div className="text-center mb-8">
        <KeyRound size={40} className="text-indigo-600 mx-auto mb-2" />
        <h1 className="text-2xl font-bold text-gray-900">Criar nova senha</h1>
      </div>
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-8">
        {done ? (
          <div className="space-y-4 text-sm">
            <p role="status" className="text-green-700">
              Senha redefinida. Por segurança, encerramos as sessões abertas em todos os aparelhos.
            </p>
            <Link to="/login" className="inline-block text-indigo-600 font-medium hover:underline">Entrar com a nova senha</Link>
          </div>
        ) : !token ? (
          <p role="alert" className="text-sm text-red-700">
            Link incompleto. <Link to="/esqueci-senha" className="underline">Peça um novo link</Link>.
          </p>
        ) : (
          <form onSubmit={submit} className="space-y-4">
            {error && <div role="alert" className="px-4 py-3 bg-red-50 text-red-700 rounded-lg text-sm">{error}</div>}
            <label className="block">
              <span className="block text-sm font-medium text-gray-700 mb-1.5">Nova senha</span>
              <input type="password" required autoComplete="new-password" maxLength={LIMITS.passwordMax} value={password}
                     onChange={e => setPassword(e.target.value)} className={input} />
            </label>
            <label className="block">
              <span className="block text-sm font-medium text-gray-700 mb-1.5">Confirme a nova senha</span>
              <input type="password" required autoComplete="new-password" maxLength={LIMITS.passwordMax} value={confirm}
                     onChange={e => setConfirm(e.target.value)} className={input} />
            </label>
            <p className="text-xs text-gray-500">Mínimo 8 caracteres, com letras e números. Evite senhas comuns.</p>
            <button disabled={loading} className="w-full py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold disabled:opacity-60">
              {loading ? 'Salvando...' : 'Salvar nova senha'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
