import { Fragment, useCallback, useEffect, useState } from 'react';
import { Search } from 'lucide-react';
import { getAdminUsers, getUserOrders, updateUser } from '../../api/admin';
import Pagination from '../../components/Pagination';
import { useAuth } from '../../contexts/AuthContext';
import type { AdminUser, Order, Page } from '../../types';
import { formatBRL, formatDate, getErrorMessage, orderStatusConfig } from '../../utils/format';

type Patch = Partial<Pick<AdminUser, 'active' | 'role'>>;

export default function AdminUsersPage() {
  const { user: me } = useAuth();
  const [data, setData] = useState<Page<AdminUser> | null>(null);
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [error, setError] = useState('');
  const [expanded, setExpanded] = useState<{ id: number; orders: Order[] | null } | null>(null);
  // Mudança sensível (trocar papel ou alterar outro admin) pede a senha e o código de quem age (revisão 4, R4-3)
  const [pending, setPending] = useState<{ user: AdminUser; patch: Patch; label: string } | null>(null);
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');

  const load = useCallback(() => {
    getAdminUsers(page, query).then(setData).catch(err => setError(getErrorMessage(err)));
  }, [page, query]);

  useEffect(load, [load]);

  const send = async (u: AdminUser, data: Parameters<typeof updateUser>[1]) => {
    setError('');
    try {
      await updateUser(u.id, data);
      setPending(null);
      load();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setPassword('');
      setCode('');
    }
  };

  const change = (u: AdminUser, patch: Patch, label: string) => {
    if (patch.role !== undefined || u.role === 'ADMIN') {
      setError('');
      setPending({ user: u, patch, label });
      return;
    }
    return send(u, patch);
  };

  const confirmSensitive = (e: React.FormEvent) => {
    e.preventDefault();
    if (pending) return send(pending.user, { ...pending.patch, password, mfaCode: code || undefined });
  };

  const toggleOrders = (u: AdminUser) => {
    if (expanded?.id === u.id) return setExpanded(null);
    setExpanded({ id: u.id, orders: null });
    getUserOrders(u.id)
      .then(orders => setExpanded(cur => (cur?.id === u.id ? { id: u.id, orders } : cur)))
      .catch(err => setError(getErrorMessage(err)));
  };

  return (
    <div>
      <form onSubmit={e => { e.preventDefault(); setPage(0); setQuery(search); }} className="relative w-full sm:max-w-xs mb-4">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Buscar por nome ou email..."
               className="w-full pl-9 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
      </form>

      {error && <div role="alert" className="mb-4 px-4 py-3 bg-red-50 text-red-700 rounded-lg text-sm">{error}</div>}

      {pending && (
        <form onSubmit={confirmSensitive} aria-label="Confirmar mudança sensível"
              className="mb-4 p-4 bg-amber-50 border border-amber-200 rounded-xl text-sm space-y-3">
          <p className="text-amber-900">
            <strong>{pending.label}</strong> — {pending.user.name}. Por segurança, confirme sua senha
            {me?.mfaEnabled ? ' e o código do app autenticador' : ''}. Todos os admins serão avisados.
          </p>
          <div className="flex flex-wrap gap-3 items-end">
            <label className="block">
              <span className="block text-gray-600 mb-1">Sua senha</span>
              <input type="password" autoComplete="current-password" required autoFocus value={password}
                     onChange={e => setPassword(e.target.value)}
                     className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
            </label>
            {me?.mfaEnabled && (
              <label className="block">
                <span className="block text-gray-600 mb-1">Código do app</span>
                <input inputMode="numeric" autoComplete="one-time-code" required value={code}
                       onChange={e => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                       className="w-32 px-3 py-2 border border-gray-300 rounded-lg text-sm tracking-widest focus:outline-none focus:ring-2 focus:ring-indigo-500" />
              </label>
            )}
            <button className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg">Confirmar</button>
            <button type="button" onClick={() => { setPending(null); setPassword(''); setCode(''); }}
                    className="px-4 py-2 border border-gray-300 rounded-lg text-gray-600 hover:bg-gray-50">Cancelar</button>
          </div>
        </form>
      )}

      <div className="bg-white rounded-xl border border-gray-200 overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-500 text-left">
            <tr>
              <th className="px-4 py-3 font-medium">Usuário</th>
              <th className="px-4 py-3 font-medium">Cadastro</th>
              <th className="px-4 py-3 font-medium">Pedidos</th>
              <th className="px-4 py-3 font-medium">Perfil</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium text-right">Ações</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {data?.content.map(u => {
              const isMe = u.email === me?.email;
              return (
                <Fragment key={u.id}>
                  <tr>
                    <td className="px-4 py-3">
                      <div className="text-gray-900 font-medium">{u.name}{isMe && <span className="text-xs text-gray-400"> (você)</span>}</div>
                      <div className="text-xs text-gray-400">{u.email}{u.phone ? ` · ${u.phone}` : ''}</div>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">{formatDate(u.createdAt)}</td>
                    <td className="px-4 py-3">
                      <button onClick={() => toggleOrders(u)} disabled={u.orderCount === 0}
                              className="text-indigo-600 hover:underline disabled:text-gray-400 disabled:no-underline">
                        {u.orderCount}
                      </button>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`text-xs font-medium px-2.5 py-0.5 rounded-full ${u.role === 'ADMIN' ? 'bg-indigo-100 text-indigo-700' : 'bg-gray-100 text-gray-600'}`}>
                        {u.role === 'ADMIN' ? 'Admin' : 'Cliente'}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`text-xs font-medium px-2.5 py-0.5 rounded-full ${u.active ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
                        {u.active ? 'Ativo' : 'Bloqueado'}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      {isMe ? <div className="text-right text-xs text-gray-400">—</div> : (
                        <div className="flex justify-end gap-2">
                          <button onClick={() => change(u, { role: u.role === 'ADMIN' ? 'CUSTOMER' : 'ADMIN' },
                                                        u.role === 'ADMIN' ? 'Tornar cliente' : 'Tornar admin')}
                                  className="px-3 py-1.5 text-xs rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50 whitespace-nowrap">
                            {u.role === 'ADMIN' ? 'Tornar cliente' : 'Tornar admin'}
                          </button>
                          <button onClick={() => change(u, { active: !u.active }, u.active ? 'Bloquear admin' : 'Desbloquear admin')}
                                  className={`px-3 py-1.5 text-xs rounded-lg border ${u.active ? 'border-red-200 text-red-600 hover:bg-red-50' : 'border-green-200 text-green-700 hover:bg-green-50'}`}>
                            {u.active ? 'Bloquear' : 'Desbloquear'}
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                  {expanded?.id === u.id && (
                    <tr className="bg-gray-50">
                      <td colSpan={6} className="px-6 py-4 text-xs text-gray-600">
                        {expanded.orders === null ? 'Carregando pedidos...' : (
                          <div className="space-y-1">
                            {expanded.orders.map(o => (
                              <div key={o.id} className="flex gap-4">
                                <span className="font-medium text-gray-800">#{o.id}</span>
                                <span>{formatDate(o.createdAt)}</span>
                                <span>{formatBRL(o.total)}</span>
                                <span>{orderStatusConfig[o.status].label}</span>
                              </div>
                            ))}
                          </div>
                        )}
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
            {data && data.content.length === 0 && (
              <tr><td colSpan={6} className="px-4 py-10 text-center text-gray-400">Nenhum usuário encontrado</td></tr>
            )}
          </tbody>
        </table>
      </div>

      <p className="text-xs text-gray-400 mt-2">Usuários bloqueados não conseguem entrar nem usar a sessão atual.</p>
      {data && <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />}
    </div>
  );
}
