import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Package } from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import FormField from '../components/FormField';
import { useValidatedForm } from '../hooks/useValidatedForm';
import { getErrorMessage } from '../utils/format';
import { LIMITS, maskPhone, validateRegister, type RegisterValues } from '../utils/validation';

const initial: RegisterValues = { name: '', email: '', password: '', confirmPassword: '', phone: '' };

const fields: {
  key: keyof RegisterValues; label: string; type: string; placeholder: string;
  autoComplete: string; maxLength?: number; hint?: string;
}[] = [
  { key: 'name', label: 'Nome completo', type: 'text', placeholder: 'João Silva', autoComplete: 'name', maxLength: LIMITS.name },
  { key: 'email', label: 'Email', type: 'email', placeholder: 'seu@email.com', autoComplete: 'email', maxLength: LIMITS.email },
  { key: 'password', label: 'Senha', type: 'password', placeholder: 'Mínimo 8 caracteres', autoComplete: 'new-password',
    maxLength: LIMITS.passwordMax, hint: 'Mínimo 8 caracteres, com letras e números.' },
  { key: 'confirmPassword', label: 'Confirmar senha', type: 'password', placeholder: 'Repita a senha', autoComplete: 'new-password',
    maxLength: LIMITS.passwordMax },
  { key: 'phone', label: 'Telefone (opcional)', type: 'tel', placeholder: '(11) 99999-9999', autoComplete: 'tel', maxLength: 15 },
];

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const form = useValidatedForm(initial, validateRegister);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [acceptTerms, setAcceptTerms] = useState(false);
  const [termsError, setTermsError] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    const formValid = form.validateAll();
    setTermsError(!acceptTerms);
    if (!formValid || !acceptTerms) return;
    const { name, email, password, phone } = form.values;
    setLoading(true);
    try {
      await register(name.trim(), email.trim().toLowerCase(), password, phone || undefined, acceptTerms);
      navigate('/');
    } catch (err) {
      setError(getErrorMessage(err, 'Erro ao cadastrar. Tente novamente.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-[80vh] flex items-center justify-center px-4 py-10">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <Package size={40} className="text-indigo-600 mx-auto mb-2" />
          <h1 className="text-2xl font-bold text-gray-900">Crie sua conta</h1>
          <p className="text-gray-500 mt-1">Rápido e gratuito</p>
        </div>

        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-8">
          {error && (
            <div className="mb-4 px-4 py-3 bg-red-50 text-red-700 rounded-lg text-sm">{error}</div>
          )}

          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            {fields.map(f => (
              <FormField
                key={f.key}
                id={`register-${f.key}`}
                label={f.label}
                type={f.type}
                placeholder={f.placeholder}
                autoComplete={f.autoComplete}
                maxLength={f.maxLength}
                hint={f.hint}
                value={form.values[f.key]}
                error={form.errorOf(f.key)}
                onChange={e => form.setValue(f.key, f.key === 'phone' ? maskPhone(e.target.value) : e.target.value)}
                onBlur={() => form.touch(f.key)}
              />
            ))}
            <div>
              <label className="flex items-start gap-2 text-sm text-gray-600">
                <input
                  type="checkbox"
                  checked={acceptTerms}
                  onChange={e => { setAcceptTerms(e.target.checked); setTermsError(false); }}
                  aria-invalid={termsError || undefined}
                  aria-describedby={termsError ? 'register-terms-error' : undefined}
                  className="mt-0.5 accent-indigo-600"
                />
                <span>
                  Li e aceito os <Link to="/termos" target="_blank" className="text-indigo-600 hover:underline">Termos de uso</Link> e
                  a <Link to="/privacidade" target="_blank" className="text-indigo-600 hover:underline">Política de privacidade</Link>.
                </span>
              </label>
              {termsError && (
                <p id="register-terms-error" role="alert" className="text-xs text-red-600 mt-1">
                  É preciso aceitar os termos e a política de privacidade.
                </p>
              )}
            </div>
            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold transition-colors disabled:opacity-60 mt-2"
            >
              {loading ? 'Criando conta...' : 'Criar Conta'}
            </button>
          </form>

          <p className="text-center text-sm text-gray-500 mt-6">
            Já tem conta?{' '}
            <Link to="/login" className="text-indigo-600 font-medium hover:underline">
              Entrar
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
