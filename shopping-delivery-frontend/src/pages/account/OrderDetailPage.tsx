import { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, Check, MapPin, XCircle } from 'lucide-react';
import { cancelOrder, getOrder, requestReturn } from '../../api/orders';
import PixPayment from '../../components/PixPayment';
import { formatBRL, formatDate, getErrorMessage, orderStatusConfig, paymentStatusConfig } from '../../utils/format';
import { ORDER_FLOW } from '../../utils/orders';
import type { Order, OrderStatus } from '../../types';

function StatusTimeline({ status }: { status: OrderStatus }) {
  if (status === 'CANCELLED') {
    return (
      <div className="flex items-center gap-2 bg-red-50 text-red-700 rounded-xl px-4 py-3 mb-4 text-sm">
        <XCircle size={18} /> Este pedido foi cancelado. Os itens voltaram ao estoque.
      </div>
    );
  }
  const current = ORDER_FLOW.indexOf(status);
  return (
    <ol aria-label="Andamento do pedido" className="flex items-center bg-white rounded-xl border border-gray-200 p-4 mb-4">
      {ORDER_FLOW.map((s, i) => {
        const done = i <= current;
        return (
          <li key={s} aria-current={i === current ? 'step' : undefined} className={`flex items-center ${i < ORDER_FLOW.length - 1 ? 'flex-1' : ''}`}>
            <div className="flex flex-col items-center gap-1">
              <span className={`w-7 h-7 rounded-full flex items-center justify-center ${done ? 'bg-indigo-600 text-white' : 'bg-gray-100 text-gray-400'}`}>
                {done ? <Check size={14} /> : i + 1}
              </span>
              <span className={`text-xs ${done ? 'text-gray-900 font-medium' : 'text-gray-400'}`}>{orderStatusConfig[s].label}</span>
            </div>
            {i < ORDER_FLOW.length - 1 && (
              <div className={`flex-1 h-0.5 mx-2 mb-5 ${i < current ? 'bg-indigo-600' : 'bg-gray-200'}`} />
            )}
          </li>
        );
      })}
    </ol>
  );
}

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionError, setActionError] = useState('');
  const [busy, setBusy] = useState(false);
  const [returnOpen, setReturnOpen] = useState(false);
  const [reason, setReason] = useState('');

  useEffect(() => {
    if (!id) return;
    getOrder(Number(id))
      .then(setOrder)
      .catch(() => navigate('/orders'))
      .finally(() => setLoading(false));
  }, [id, navigate]);

  if (loading) {
    return (
      <div className="space-y-3 animate-pulse" aria-busy="true">
        <div className="h-6 bg-gray-100 rounded w-1/4" />
        <div className="h-32 bg-gray-100 rounded-xl" />
        <div className="h-48 bg-gray-100 rounded-xl" />
      </div>
    );
  }

  if (!order) return null;

  const { label, className } = orderStatusConfig[order.status];
  const units = order.items.reduce((n, i) => n + i.quantity, 0);

  const act = async (action: () => Promise<typeof order>) => {
    setBusy(true);
    setActionError('');
    try {
      setOrder(await action());
      setReturnOpen(false);
    } catch (err) {
      setActionError(getErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const handleCancel = () => {
    const paid = order.payment?.status === 'PAID';
    if (!window.confirm(`Cancelar o pedido #${order.id}?${paid ? ' O valor pago será devolvido por Pix.' : ''}`)) return;
    return act(() => cancelOrder(order.id));
  };

  return (
    <div>
      <Link to="/orders" className="inline-flex items-center gap-2 text-sm text-gray-500 hover:text-gray-700 mb-6">
        <ArrowLeft size={16} /> Meus pedidos
      </Link>

      <div className="flex items-start justify-between mb-6">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Pedido #{order.id}</h2>
          <p className="text-gray-500 text-sm mt-1">{new Date(order.createdAt).toLocaleString('pt-BR')}</p>
        </div>
        <span className={`text-sm font-medium px-3 py-1 rounded-full ${className}`}>{label}</span>
      </div>

      <StatusTimeline status={order.status} />

      {/* Pagamento */}
      {order.payment && (
        order.payment.status === 'PENDING' ? (
          <div className="mb-4">
            <PixPayment
              orderId={order.id}
              payment={order.payment}
              // pago ou expirado: recarrega o pedido para atualizar status e linha do tempo
              onStatusChange={() => getOrder(order.id).then(setOrder).catch(() => undefined)}
            />
          </div>
        ) : (
          <div className="flex items-center justify-between bg-white rounded-xl border border-gray-200 px-4 py-3 mb-4 text-sm">
            <span className="text-gray-500">Pagamento via Pix</span>
            <span className={`text-xs font-medium px-2.5 py-0.5 rounded-full ${paymentStatusConfig[order.payment.status].className}`}>
              {paymentStatusConfig[order.payment.status].label}
            </span>
          </div>
        )
      )}

      {/* Endereço de entrega */}
      <div className="bg-white rounded-xl border border-gray-200 p-4 mb-4">
        <div className="flex items-center gap-2 text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">
          <MapPin size={13} /> Endereço de entrega
        </div>
        <p className="text-sm text-gray-700">
          {order.address.street}{order.address.number ? `, ${order.address.number}` : ''}
          {order.address.complement ? ` — ${order.address.complement}` : ''}
        </p>
        <p className="text-sm text-gray-500 mt-0.5">
          {order.address.neighborhood} · {order.address.city}/{order.address.state} · CEP {order.address.zipCode}
        </p>
      </div>

      {/* Itens */}
      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden mb-4">
        <div className="px-4 py-3 border-b border-gray-100 flex justify-between">
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">Itens</p>
          <p className="text-xs text-gray-500">{units} {units === 1 ? 'unidade' : 'unidades'}</p>
        </div>
        {order.items.map(item => (
          <div key={item.id} className="flex items-center justify-between px-4 py-3 border-b border-gray-50 last:border-0">
            <div>
              <Link to={`/products/${item.productId}`} className="text-sm font-medium text-gray-900 hover:text-indigo-600">
                {item.productName}
              </Link>
              <p className="text-xs text-gray-500">{item.quantity}x {formatBRL(item.unitPrice)}</p>
            </div>
            <span className="text-sm font-semibold text-gray-700">{formatBRL(item.subtotal)}</span>
          </div>
        ))}
        <div className="px-4 py-3 bg-gray-50 flex justify-between font-bold">
          <span>Total</span>
          <span className="text-indigo-600 text-lg">{formatBRL(order.total)}</span>
        </div>
      </div>

      {/* Cancelamento e direito de arrependimento (CDC art. 49) */}
      {actionError && <p role="alert" className="mb-4 text-sm bg-red-50 text-red-700 rounded-lg px-4 py-2">{actionError}</p>}
      {order.returnRequestedAt && (
        <p role="status" className="mb-4 text-sm bg-blue-50 text-blue-700 rounded-lg px-4 py-3">
          Devolução solicitada em {formatDate(order.returnRequestedAt)}. Vamos enviar as instruções por email; o valor é
          devolvido por Pix assim que a devolução for registrada.
        </p>
      )}
      {order.canRequestReturn && order.returnDeadline && (
        <div className="mb-4 bg-white rounded-xl border border-gray-200 p-4 text-sm">
          <p className="text-gray-600">
            Você pode desistir desta compra até <strong>{formatDate(order.returnDeadline)}</strong> (7 dias após a entrega),
            sem precisar justificar. <Link to="/trocas-e-devolucoes" className="text-indigo-600 hover:underline">Saiba mais</Link>
          </p>
          {returnOpen ? (
            <div className="mt-3 space-y-2">
              <label htmlFor="return-reason" className="block text-gray-600">Motivo (opcional)</label>
              <textarea id="return-reason" rows={2} maxLength={500} value={reason} onChange={e => setReason(e.target.value)}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
              <div className="flex gap-2">
                <button onClick={() => act(() => requestReturn(order.id, reason))} disabled={busy}
                        className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg disabled:opacity-50">
                  Confirmar devolução
                </button>
                <button onClick={() => setReturnOpen(false)} className="px-4 py-2 border border-gray-300 rounded-lg">Voltar</button>
              </div>
            </div>
          ) : (
            <button onClick={() => setReturnOpen(true)} className="mt-3 px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50">
              Solicitar devolução
            </button>
          )}
        </div>
      )}

      <div className="flex flex-wrap gap-3">
      {order.canCancel && (
        <button onClick={handleCancel} disabled={busy}
                className="px-5 py-2.5 border border-red-300 text-red-700 hover:bg-red-50 rounded-xl text-sm font-medium disabled:opacity-50">
          Cancelar pedido
        </button>
      )}
      <Link
        to="/"
        className="inline-block px-5 py-2.5 border border-gray-300 text-gray-700 hover:bg-gray-50 rounded-xl text-sm font-medium transition-colors"
      >
        Continuar comprando
      </Link>
      </div>
    </div>
  );
}
