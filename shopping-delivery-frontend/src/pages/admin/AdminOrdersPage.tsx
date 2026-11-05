import { Fragment, useCallback, useEffect, useState } from 'react';
import { confirmPayment, getAdminOrders, refundPayment, updateOrderStatus } from '../../api/admin';
import Pagination from '../../components/Pagination';
import type { AdminOrder, OrderStatus, Page } from '../../types';
import { ORDER_STATUSES, formatBRL, formatDate, getErrorMessage, orderStatusConfig, paymentStatusConfig } from '../../utils/format';

const filters: (OrderStatus | undefined)[] = [undefined, 'PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

export default function AdminOrdersPage() {
  const [data, setData] = useState<Page<AdminOrder> | null>(null);
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<OrderStatus | undefined>();
  const [expanded, setExpanded] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [savingId, setSavingId] = useState<number | null>(null);

  const load = useCallback(() => {
    getAdminOrders(page, status).then(setData).catch(err => setError(getErrorMessage(err)));
  }, [page, status]);

  useEffect(load, [load]);

  const changeStatus = async (order: AdminOrder, next: OrderStatus) => {
    setError('');
    setSuccess('');
    if (next === 'CANCELLED' && !window.confirm(
      `Cancelar o pedido #${order.id}? Os itens voltam ao estoque e a ação não pode ser desfeita.`,
    )) return;
    setSavingId(order.id);
    try {
      const updated = await updateOrderStatus(order.id, next);
      setSuccess(`Pedido #${order.id} atualizado para ${orderStatusConfig[updated.status].label}.`);
      load(); // recarrega: com filtro ativo o pedido pode sair da lista
    } catch (err) {
      setError(getErrorMessage(err, 'Não foi possível alterar o status do pedido.'));
    } finally {
      setSavingId(null);
    }
  };

  /** Pix estático: o admin confirma depois de ver o valor e o identificador no extrato do banco. */
  const handleConfirmPayment = async (order: AdminOrder) => {
    setError('');
    setSuccess('');
    if (!window.confirm(
      `Confirmar o Pix do pedido #${order.id}? Faça isso só depois de ver ${formatBRL(order.total)} no extrato `
      + `(identificador ${order.payment?.txid}).`,
    )) return;
    setSavingId(order.id);
    try {
      await confirmPayment(order.id);
      setSuccess(`Pagamento do pedido #${order.id} confirmado. O pedido foi para Confirmado.`);
      load();
    } catch (err) {
      setError(getErrorMessage(err, 'Não foi possível confirmar o pagamento.'));
    } finally {
      setSavingId(null);
    }
  };

  /** Registra que o Pix foi devolvido pelo app do banco (estorno pendente ou arrependimento). */
  const handleRefund = async (order: AdminOrder) => {
    setError('');
    setSuccess('');
    if (!window.confirm(`Confirmar que o Pix de ${formatBRL(order.total)} do pedido #${order.id} já foi devolvido ao cliente?`)) return;
    setSavingId(order.id);
    try {
      await refundPayment(order.id);
      setSuccess(`Estorno do pedido #${order.id} registrado.`);
      load();
    } catch (err) {
      setError(getErrorMessage(err, 'Não foi possível registrar o estorno.'));
    } finally {
      setSavingId(null);
    }
  };

  const canRefund = (o: AdminOrder) =>
    o.payment?.status === 'REFUND_PENDING' || (o.payment?.status === 'PAID' && !!o.returnRequestedAt);

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

      {error && <div role="alert" className="mb-4 px-4 py-3 bg-red-50 text-red-700 rounded-lg text-sm">{error}</div>}
      {success && <div role="status" className="mb-4 px-4 py-3 bg-green-50 text-green-700 rounded-lg text-sm">{success}</div>}

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
              const allowed = o.nextStatuses;
              const final = allowed.length === 0;
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
                      {o.returnRequestedAt && (
                        <span className="block w-fit mt-1 text-xs font-medium px-2.5 py-0.5 rounded-full bg-blue-100 text-blue-700">
                          Devolução solicitada
                        </span>
                      )}
                      {o.payment && (
                        <span className={`block w-fit mt-1 text-xs font-medium px-2.5 py-0.5 rounded-full ${paymentStatusConfig[o.payment.status].className}`}>
                          {paymentStatusConfig[o.payment.status].label}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right" onClick={e => e.stopPropagation()}>
                      {/* Todos os status aparecem; os que o back-end não permite ficam desabilitados */}
                      <select
                        aria-label={`Status do pedido #${o.id}`}
                        value={o.status}
                        disabled={final || savingId === o.id}
                        title={final ? 'Pedido finalizado: o status não pode mais ser alterado' : undefined}
                        onChange={e => changeStatus(o, e.target.value as OrderStatus)}
                        className="px-2 py-1.5 border border-gray-300 rounded-lg text-xs bg-white disabled:bg-gray-50 disabled:text-gray-400"
                      >
                        {ORDER_STATUSES.map(s => (
                          <option key={s} value={s} disabled={s !== o.status && !allowed.includes(s)}>
                            {orderStatusConfig[s].label}{s === o.status ? ' (atual)' : ''}
                          </option>
                        ))}
                      </select>
                      {final && <span className="block text-xs text-gray-400 mt-1">Finalizado</span>}
                      {canRefund(o) && (
                        <button
                          onClick={() => handleRefund(o)}
                          disabled={savingId === o.id}
                          className="block ml-auto mt-1 text-xs text-red-700 hover:underline disabled:opacity-50"
                        >
                          Registrar estorno
                        </button>
                      )}
                      {o.payment?.status === 'PENDING' && (
                        <button
                          onClick={() => handleConfirmPayment(o)}
                          disabled={savingId === o.id}
                          className="block ml-auto mt-1 text-xs text-green-700 hover:underline disabled:opacity-50"
                        >
                          Confirmar Pix
                        </button>
                      )}
                      {savingId === o.id && <span className="block text-xs text-gray-400 mt-1">Salvando…</span>}
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
                          {o.payment && (
                            <div className="sm:col-span-2">
                              <p className="font-semibold text-gray-700 mb-1">Pagamento</p>
                              <p>Pix · {paymentStatusConfig[o.payment.status].label} · identificador {o.payment.txid}</p>
                              {o.payment.paidAt && <p>Pago em {new Date(o.payment.paidAt).toLocaleString('pt-BR')}</p>}
                              {o.returnReason && <p>Motivo da devolução: {o.returnReason}</p>}
                              {o.payment.status === 'REFUND_PENDING' && (
                                <p className="text-red-600">Pix recebido para pedido cancelado: devolva o valor pelo app do banco.</p>
                              )}
                            </div>
                          )}
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

      <p className="text-xs text-gray-400 mt-2">
        Clique em um pedido para ver itens e endereço. O pedido só avança (Pendente → Confirmado → Enviado → Entregue)
        e pode pular etapas; cancelar só é possível antes do envio e devolve os itens ao estoque. Pedido com Pix
        pendente só pode ser cancelado — ele avança sozinho quando o pagamento é confirmado (webhook ou "Confirmar Pix").
      </p>
      {data && <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />}
    </div>
  );
}
