import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Package } from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { getErrorMessage } from '../utils/format';

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({ name: '', email: '', password: '', phone: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (form.password.length < 6) {
      setError('A senha deve ter no mínimo 6 caracteres.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      await register(form.name, form.email, form.password, form.phone || undefined);
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

          <form onSubmit={handleSubmit} className="space-y-4">
            {[
              { key: 'name',  label: 'Nome completo',   type: 'text',     placeholder: 'João Silva',         required: true },
              { key: 'email', label: 'Email',            type: 'email',    placeholder: 'seu@email.com',      required: true },
              { key: 'password', label: 'Senha',         type: 'password', placeholder: 'Mínimo 6 caracteres', required: true },
              { key: 'phone', label: 'Telefone (opcional)', type: 'tel',  placeholder: '(11) 99999-9999',    required: false },
            ].map(field => (
              <div key={field.key}>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">{field.label}</label>
                <input
                  type={field.type}
                  value={form[field.key as keyof typeof form]}
                  onChange={e => setForm(f => ({ ...f, [field.key]: e.target.value }))}
                  required={field.required}
                  placeholder={field.placeholder}
                  className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>
            ))}
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
