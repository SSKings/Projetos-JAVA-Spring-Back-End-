import { useState, type ReactNode } from 'react';

/**
 * Confirmação de identidade para ações sensíveis do admin (revisões 4 e 5): senha e, com MFA ativo, um código do
 * app autenticador. O back-end exige os dois; aqui só coletamos. Os campos são limpos a cada tentativa.
 */
export default function StepUpForm({ label, children, mfa, onConfirm, onCancel }: {
  label: string;
  children: ReactNode;
  mfa: boolean;
  onConfirm: (password: string, mfaCode?: string) => Promise<unknown> | void;
  onCancel: () => void;
}) {
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await onConfirm(password, code || undefined);
    } finally {
      setPassword('');
      setCode('');
    }
  };

  return (
    <form onSubmit={submit} aria-label={label}
          className="mb-4 p-4 bg-amber-50 border border-amber-200 rounded-xl text-sm space-y-3">
      <div className="text-amber-900">
        {children} Por segurança, confirme sua senha{mfa ? ' e o código do app autenticador' : ''}. Todos os admins serão avisados.
      </div>
      <div className="flex flex-wrap gap-3 items-end">
        <label className="block">
          <span className="block text-gray-600 mb-1">Sua senha</span>
          <input type="password" autoComplete="current-password" required autoFocus value={password}
                 onChange={e => setPassword(e.target.value)}
                 className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
        </label>
        {mfa && (
          <label className="block">
            <span className="block text-gray-600 mb-1">Código do app</span>
            <input inputMode="numeric" autoComplete="one-time-code" required value={code}
                   onChange={e => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                   className="w-32 px-3 py-2 border border-gray-300 rounded-lg text-sm tracking-widest focus:outline-none focus:ring-2 focus:ring-indigo-500" />
          </label>
        )}
        <button className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg">Confirmar</button>
        <button type="button" onClick={onCancel}
                className="px-4 py-2 border border-gray-300 rounded-lg text-gray-600 hover:bg-gray-50">Cancelar</button>
      </div>
    </form>
  );
}
