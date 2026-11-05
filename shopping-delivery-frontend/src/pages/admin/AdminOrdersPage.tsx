import { Fragment, useCallback, useEffect, useState } from 'react';
import { getAdminOrders, updateOrderStatus } from '../../api/admin';
import Pagination from '../../components/Pagination';
import type { AdminOrder, OrderStatus, Page } from '../../types';
import { formatBRL, formatDate, getErrorMessage, nextOrderStatuses, orderStatusConfig } from '../../utils/format';

const filters: (OrderStatus | undefined)[] = [undefined, 'PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

export default function AdminOrdersPage() {
  const [data, setData] = useState<Page<AdminOrder> | null>(null);
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<OrderStatus | undefined>();
  const [expanded, setExpanded] = useState<number | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    getAdminOrders(page, status).then(setData).catch(err => setError(getErrorMessage(err)));
  }, [page, status]);

  useEffect(load, [load]);

  const changeStatus = async (order: AdminOrder, next: OrderStatus) => {
    setError('');
    try {
      await updateOrderStatus(order.id, next);
      load();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  return (
    <div>
      <div className="flex flex-wrap gap-2 mb-4">
        {filters.map(f => (
          <button key={f ?? 'ALL'} onClick={() => { setPage(0); setStatus(f); }}
                  className={`px-3 py-1.5 rounded-full text-xs font-medium border ${
                    status === f ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white text-gray-600 border-gray-200 hover:bg-gray-50'
                  }`}>
            {f ? orderStatusConfig[f].label : 'Todos'}
          </button>
        ))}
      </div>

      {error && <div className="mb-4 px-4 py-3 bg-red-50 text-red-700 rounded-lg text-sm">{error}</div>}

      <div className="bg-white rounded-xl border border-gray-200 overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-500 text-left">
            <tr>
              <th className="px-4 py-3 font-medium">Pedido</th>
              <th className="px-4 py-3 font-medium">Cliente</th>
              <th className="px-4 py-3 font-medium">Data</th>
              <th className="px-4 py-3 font-medium">Total</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium text-right">Alterar status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {data?.content.map(o => {
              const { label, className } = orderStatusConfig[o.status];
              const options = nextOrderStatuses[o.status];
              return (
                <Fragment key={o.id}>
                  <tr className="hover:bg-gray-50 cursor-pointer" onClick={() => setExpanded(expanded === o.id ? null : o.id)}>
                    <td className="px-4 py-3 font-medium text-gray-900">#{o.id}</td>
                    <td className="px-4 py-3">
                      <div className="text-gray-900">{o.customerName}</div>
                      <div className="text-xs text-gray-400">{o.customerEmail}</div>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">{formatDate(o.createdAt)}</td>
                    <td className="px-4 py-3 whitespace-nowrap font-medium">{formatBRL(o.total)}</td>
                    <td className="px-4 py-3">
                      <span className={`text-xs font-medium px-2.5 py-0.5 rounded-full ${className}`}>{label}</span>
                    </td>
                    <td className="px-4 py-3 text-right" onClick={e => e.stopPropagation()}>
                      {options.length > 0 ? (
                        <select value="" onChange={e => changeStatus(o, e.target.value as OrderStatus)}
                                className="px-2 py-1.5 border border-gray-300 rounded-lg text-xs bg-white">
                          <option value="" disabled>Mover para…</option>
                          {options.map(s => <option key={s} value={s}>{orderStatusConfig[s].label}</option>)}
                        </select>
                      ) : <span className="text-xs text-gray-400">Finalizado</span>}
                    </td>
                  </tr>
                  {expanded === o.id && (
                    <tr className="bg-gray-50">
                      <td colSpan={6} className="px-6 py-4">
                        <div className="grid sm:grid-cols-2 gap-4 text-xs text-gray-600">
                          <div>
                            <p className="font-semibold text-gray-700 mb-1">Itens</p>
                            {o.items.map(i => (
                              <p key={i.id}>{i.quantity}× {i.productName} — {formatBRL(i.subtotal)}</p>
                            ))}
                          </div>
                          <div>
                            <p className="font-semibold text-gray-700 mb-1">Entrega</p>
                            <p>{o.address.street}, {o.address.number} {o.address.complement}</p>
                            <p>{o.address.neighborhood} — {o.address.city}/{o.address.state}</p>
                            <p>CEP {o.address.zipCode}</p>
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
            {data && data.content.length === 0 && (
              <tr><td colSpan={6} className="px-4 py-10 text-center text-gray-400">Nenhum pedido encontrado</td></tr>
            )}
          </tbody>
        </table>
      </div>

      <p className="text-xs text-gray-400 mt-2">Clique em um pedido para ver itens e endereço. Cancelar devolve os itens ao estoque.</p>
      {data && <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />}
    </div>
  );
}
