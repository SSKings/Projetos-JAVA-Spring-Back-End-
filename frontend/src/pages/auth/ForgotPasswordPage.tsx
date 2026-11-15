import { useState } from 'react';
import { Link } from 'react-router-dom';
import { KeyRound } from 'lucide-react';
import { forgotPassword } from '../../api/auth';
import { getErrorMessage } from '../../utils/format';

/** "Esqueci minha senha" (ADR-025). A mensagem é a mesma exista a conta ou não. */
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await forgotPassword(email.trim());
      setSent(true);
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
        <h1 className="text-2xl font-bold text-gray-900">Esqueceu a senha?</h1>
        <p className="text-gray-500 mt-1">Enviamos um link para você criar uma nova.</p>
      </div>
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-8">
        {sent ? (
          <p role="status" className="text-sm text-gray-700">
            Se houver uma conta com <strong>{email.trim()}</strong>, enviamos um link para redefinir a senha. Ele vale
            30 minutos. Confira também a caixa de spam.
          </p>
        ) : (
          <form onSubmit={submit} className="space-y-4">
            {error && <div role="alert" className="px-4 py-3 bg-red-50 text-red-700 rounded-lg text-sm">{error}</div>}
            <label className="block">
              <span className="block text-sm font-medium text-gray-700 mb-1.5">Email da sua conta</span>
              <input type="email" required autoComplete="email" value={email} onChange={e => setEmail(e.target.value)}
                     placeholder="seu@email.com"
                     className="w-full px-4 py-3 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
            </label>
            <button disabled={loading} className="w-full py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold disabled:opacity-60">
              {loading ? 'Enviando...' : 'Enviar link'}
            </button>
          </form>
        )}
        <p className="text-center text-sm text-gray-500 mt-6">
          <Link to="/login" className="text-indigo-600 font-medium hover:underline">Voltar para o login</Link>
        </p>
      </div>
    </div>
  );
}
