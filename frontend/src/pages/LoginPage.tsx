import { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { Package } from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { getErrorMessage } from '../utils/format';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: { pathname: string } })?.from?.pathname || '/';

  const [form, setForm] = useState({ email: '', password: '', mfaCode: '' });
  const [needsMfa, setNeedsMfa] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await login(form.email, form.password, needsMfa ? form.mfaCode : undefined);
      navigate(from, { replace: true });
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } }).response?.data?.message;
      if (message === 'MFA code required') {
        setNeedsMfa(true);   // senha certa: falta só o código do app
      } else {
        setError(getErrorMessage(err, 'Email ou senha inválidos.'));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-[80vh] flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <Package size={40} className="text-indigo-600 mx-auto mb-2" />
          <h1 className="text-2xl font-bold text-gray-900">Bem-vindo de volta!</h1>
          <p className="text-gray-500 mt-1">Entre com sua conta para continuar</p>
        </div>

        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-8">
          {error && (
            <div className="mb-4 px-4 py-3 bg-red-50 text-red-700 rounded-lg text-sm">{error}</div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="login-email" className="block text-sm font-medium text-gray-700 mb-1.5">Email</label>
              <input
                id="login-email"
                type="email"
                value={form.email}
                onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
                required
                placeholder="seu@email.com"
                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>
            <div>
              <label htmlFor="login-password" className="block text-sm font-medium text-gray-700 mb-1.5">Senha</label>
              <input
                id="login-password"
                type="password"
                value={form.password}
                onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
                required
                placeholder="••••••"
                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>
            {needsMfa && (
              <div>
                <label htmlFor="login-mfa" className="block text-sm font-medium text-gray-700 mb-1.5">Código de verificação</label>
                <input
                  id="login-mfa"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  autoFocus
                  maxLength={6}
                  value={form.mfaCode}
                  onChange={e => setForm(f => ({ ...f, mfaCode: e.target.value.replace(/\D/g, '') }))}
                  required
                  placeholder="000000"
                  className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm tracking-widest focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
                <p className="text-xs text-gray-500 mt-1">Abra seu app autenticador e digite o código de 6 dígitos.</p>
              </div>
            )}
            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold transition-colors disabled:opacity-60 mt-2"
            >
              {loading ? 'Entrando...' : 'Entrar'}
            </button>
          </form>

          <p className="text-center text-sm mt-4">
            <Link to="/esqueci-senha" className="text-indigo-600 hover:underline">Esqueci minha senha</Link>
          </p>

          <p className="text-center text-sm text-gray-500 mt-6">
            Não tem conta?{' '}
            <Link to="/register" className="text-indigo-600 font-medium hover:underline">
              Cadastre-se
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
