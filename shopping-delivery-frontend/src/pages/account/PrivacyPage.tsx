import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Download, Trash2 } from 'lucide-react';
import { deleteAccount, exportData, getProfile, updateProfile } from '../../api/account';
import FormField from '../../components/FormField';
import { useAuth } from '../../contexts/AuthContext';
import { formatDate, getErrorMessage } from '../../utils/format';
import { LIMITS, maskPhone } from '../../utils/validation';
import type { Profile } from '../../types';

/** Direitos do titular (LGPD art. 18): acesso, correção, portabilidade e eliminação. */
export default function PrivacyPage() {
  const { user, updateName, clearSession } = useAuth();
  const navigate = useNavigate();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [form, setForm] = useState({ name: '', phone: '' });
  const [message, setMessage] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [del, setDel] = useState({ password: '', mfaCode: '', confirmed: false });

  useEffect(() => {
    getProfile()
      .then(p => { setProfile(p); setForm({ name: p.name, phone: p.phone ?? '' }); })
      .catch(err => setMessage({ kind: 'error', text: getErrorMessage(err) }));
  }, []);

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    setMessage(null);
    try {
      await action();
    } catch (err) {
      setMessage({ kind: 'error', text: getErrorMessage(err) });
    } finally {
      setBusy(false);
    }
  };

  const save = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) {
      setMessage({ kind: 'error', text: 'Informe seu nome.' });
      return;
    }
    return run(async () => {
      const updated = await updateProfile({ name: form.name.trim(), phone: form.phone || undefined });
      setProfile(updated);
      updateName(updated.name);
      setMessage({ kind: 'ok', text: 'Dados atualizados.' });
    });
  };

  const download = () => run(async () => {
    const data = await exportData();
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'meus-dados-shopeasy.json';
    a.click();
    URL.revokeObjectURL(url);
    setMessage({ kind: 'ok', text: 'Arquivo com seus dados gerado.' });
  });

  const remove = (e: React.FormEvent) => {
    e.preventDefault();
    return run(async () => {
      await deleteAccount(del.password, user?.mfaEnabled ? del.mfaCode : undefined);
      clearSession();   // o servidor já revogou o token
      navigate('/', { replace: true, state: { accountDeleted: true } });
    });
  };

  return (
    <div className="space-y-5 text-sm">
      {message && (
        <p role={message.kind === 'ok' ? 'status' : 'alert'}
           className={`rounded-lg px-4 py-2 ${message.kind === 'ok' ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'}`}>
          {message.text}
        </p>
      )}

      <section className="bg-white rounded-xl border border-gray-200 p-5">
        <h2 className="text-lg font-semibold text-gray-900 mb-3">Meus dados</h2>
        <form onSubmit={save} className="grid sm:grid-cols-2 gap-3 items-end">
          <FormField id="profile-name" label="Nome" value={form.name} maxLength={LIMITS.name} autoComplete="name"
                     onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
          <FormField id="profile-phone" label="Telefone" type="tel" value={form.phone} maxLength={15} autoComplete="tel"
                     onChange={e => setForm(f => ({ ...f, phone: maskPhone(e.target.value) }))} />
          <p className="text-gray-500 sm:col-span-2">
            Email: <strong>{profile?.email ?? user?.email}</strong>
            {profile?.termsAcceptedAt && <> · termos aceitos em {formatDate(profile.termsAcceptedAt)} (versão {profile.termsVersion})</>}
          </p>
          <button disabled={busy} className="w-fit px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg disabled:opacity-50">
            Salvar
          </button>
        </form>
      </section>

      <section className="bg-white rounded-xl border border-gray-200 p-5">
        <h2 className="text-lg font-semibold text-gray-900 mb-1">Baixar meus dados</h2>
        <p className="text-gray-500 mb-3">
          Arquivo JSON com cadastro, endereços, pedidos e eventos de segurança da sua conta.
        </p>
        <button onClick={download} disabled={busy}
                className="flex items-center gap-2 px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50">
          <Download size={16} /> Baixar meus dados
        </button>
      </section>

      <section className="bg-white rounded-xl border border-red-200 p-5">
        <h2 className="text-lg font-semibold text-red-700 mb-1">Excluir minha conta</h2>
        <p className="text-gray-600 mb-3">
          Seus dados pessoais serão anonimizados e você não poderá mais entrar. Pedidos já feitos continuam registrados,
          sem seu nome e email, pelo prazo exigido por lei (veja a <Link to="/privacidade" className="text-indigo-600 hover:underline">
          política de privacidade</Link>). Não é possível excluir com pedidos em andamento.
        </p>
        <form onSubmit={remove} className="space-y-3">
          <div className="flex flex-wrap gap-3">
            <FormField id="delete-password" label="Confirme sua senha" type="password" autoComplete="current-password"
                       value={del.password} onChange={e => setDel(d => ({ ...d, password: e.target.value }))} />
            {user?.mfaEnabled && (
              <FormField id="delete-mfa" label="Código do app" inputMode="numeric" autoComplete="one-time-code"
                         value={del.mfaCode} onChange={e => setDel(d => ({ ...d, mfaCode: e.target.value.replace(/\D/g, '').slice(0, 6) }))} />
            )}
          </div>
          <label className="flex items-center gap-2 text-gray-600">
            <input type="checkbox" checked={del.confirmed} onChange={e => setDel(d => ({ ...d, confirmed: e.target.checked }))}
                   className="accent-red-600" />
            Entendo que a exclusão é definitiva.
          </label>
          <button disabled={busy || !del.confirmed || !del.password}
                  className="flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg disabled:opacity-50">
            <Trash2 size={16} /> Excluir minha conta
          </button>
        </form>
      </section>
    </div>
  );
}
