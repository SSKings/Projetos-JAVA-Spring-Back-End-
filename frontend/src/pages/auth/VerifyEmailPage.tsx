import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { MailCheck, MailX } from 'lucide-react';
import { verifyEmail } from '../../api/auth';
import { useAuth } from '../../contexts/AuthContext';
import { getErrorMessage } from '../../utils/format';

/**
 * Abre o link de confirmação do e-mail (ADR-025). O token vem no fragmento (#token=…), que o navegador não manda ao
 * servidor (não fica no log do nginx), e sai da barra de endereço na hora (histórico).
 */
export default function VerifyEmailPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { markEmailVerified, isAuthenticated } = useAuth();
  const [result, setResult] = useState<{ ok: boolean; text: string } | null>(null);
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;                  // o link vale uma vez: não chamar duas vezes (StrictMode)
    started.current = true;
    const token = new URLSearchParams(location.hash.slice(1)).get('token') ?? '';
    navigate(location.pathname, { replace: true });
    verifyEmail(token)
      .then(() => {
        markEmailVerified();
        setResult({ ok: true, text: 'E-mail confirmado! Você já pode fechar pedidos.' });
      })
      .catch(err => setResult({ ok: false, text: getErrorMessage(err) }));
  }, [navigate, location.hash, location.pathname, markEmailVerified]);

  return (
    <div className="max-w-md mx-auto px-4 py-16 text-center">
      {!result ? <p className="text-gray-500">Confirmando seu e-mail...</p> : (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-8 space-y-4">
          {result.ok ? <MailCheck size={40} className="mx-auto text-green-600" /> : <MailX size={40} className="mx-auto text-red-600" />}
          <p role={result.ok ? 'status' : 'alert'} className={result.ok ? 'text-green-700' : 'text-red-700'}>{result.text}</p>
          <Link to={isAuthenticated ? '/' : '/login'} className="inline-block text-indigo-600 font-medium hover:underline">
            {isAuthenticated ? 'Continuar comprando' : 'Entrar'}
          </Link>
        </div>
      )}
    </div>
  );
}
