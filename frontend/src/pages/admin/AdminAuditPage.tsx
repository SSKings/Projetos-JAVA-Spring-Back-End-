import { useEffect, useState } from 'react';
import { getAuditLogs } from '../../api/admin';
import Pagination from '../../components/Pagination';
import { getErrorMessage } from '../../utils/format';
import type { AuditLog, Page } from '../../types';

const AUDIT_ACTIONS: Record<string, string> = {
  LOGIN_SUCCESS: 'Login',
  LOGIN_FAILED: 'Login recusado',
  ACCOUNT_LOCKED: 'Conta bloqueada (tentativas)',
  LOGOUT: 'Logout',
  REGISTER: 'Cadastro',
  MFA_ENABLED: 'MFA ativado',
  MFA_DISABLED: 'MFA desativado',
  ORDER_STATUS_CHANGED: 'Status do pedido',
  ORDER_CANCELLED_BY_CUSTOMER: 'Cancelado pelo cliente',
  RETURN_REQUESTED: 'Devolução solicitada',
  PAYMENT_CONFIRMED: 'Pix confirmado',
  PAYMENT_EXPIRED: 'Pix expirado',
  PAYMENT_REFUND_PENDING: 'Estorno pendente',
  PAYMENT_REFUNDED: 'Pix estornado',
  WEBHOOK_REJECTED: 'Webhook rejeitado',
  USER_UPDATED: 'Usuário alterado',
  PRODUCT_CHANGED: 'Produto alterado',
  PROFILE_UPDATED: 'Perfil atualizado',
  DATA_EXPORTED: 'Dados exportados',
  ACCOUNT_DELETED: 'Conta excluída',
};

/** Trilha de auditoria: quem fez o quê, quando e de qual IP (somente leitura). */
export default function AdminAuditPage() {
  const [data, setData] = useState<Page<AuditLog> | null>(null);
  const [page, setPage] = useState(0);
  const [action, setAction] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    getAuditLogs(page, action)
      .then(d => { setData(d); setError(''); })
      .catch(err => setError(getErrorMessage(err)));
  }, [page, action]);

  return (
    <div>
      <label className="flex items-center gap-2 text-sm text-gray-600 mb-4">
        Ação
        <select value={action} onChange={e => { setPage(0); setAction(e.target.value); }}
                className="px-2 py-1.5 border border-gray-300 rounded-lg text-sm bg-white">
          <option value="">Todas</option>
          {Object.entries(AUDIT_ACTIONS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>

      {error && <div role="alert" className="mb-4 px-4 py-3 bg-red-50 text-red-700 rounded-lg text-sm">{error}</div>}

      <div className="bg-white rounded-xl border border-gray-200 overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-500 text-left">
            <tr>
              <th className="px-4 py-3 font-medium">Quando</th>
              <th className="px-4 py-3 font-medium">Ação</th>
              <th className="px-4 py-3 font-medium">Quem</th>
              <th className="px-4 py-3 font-medium">Alvo</th>
              <th className="px-4 py-3 font-medium">Detalhes</th>
              <th className="px-4 py-3 font-medium">IP</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {data?.content.map(log => (
              <tr key={log.id}>
                <td className="px-4 py-2 whitespace-nowrap">{new Date(log.createdAt).toLocaleString('pt-BR')}</td>
                <td className="px-4 py-2">{AUDIT_ACTIONS[log.action] ?? log.action}</td>
                <td className="px-4 py-2">{log.actorEmail ?? <span className="text-gray-400">sistema</span>}</td>
                <td className="px-4 py-2 whitespace-nowrap">{log.targetType ? `${log.targetType} #${log.targetId ?? '—'}` : '—'}</td>
                <td className="px-4 py-2 text-gray-500">{log.details ?? ''}</td>
                <td className="px-4 py-2 text-gray-500 font-mono text-xs">{log.ip ?? ''}</td>
              </tr>
            ))}
            {data && data.content.length === 0 && (
              <tr><td colSpan={6} className="px-4 py-10 text-center text-gray-400">Nenhum registro</td></tr>
            )}
          </tbody>
        </table>
      </div>
      {data && <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />}
    </div>
  );
}
