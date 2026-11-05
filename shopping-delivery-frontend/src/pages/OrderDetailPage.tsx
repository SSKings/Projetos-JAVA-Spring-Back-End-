import { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, MapPin } from 'lucide-react';
import { getOrder } from '../api/orders';
import type { Order, OrderStatus } from '../types';

const statusConfig: Record<OrderStatus, { label: string; className: string }> = {
  PENDING:   { label: 'Pendente',   className: 'bg-yellow-100 text-yellow-700' },
  CONFIRMED: { label: 'Confirmado', className: 'bg-blue-100 text-blue-700' },
  SHIPPED:   { label: 'Enviado',    className: 'bg-purple-100 text-purple-700' },
  DELIVERED: { label: 'Entregue',   className: 'bg-green-100 text-green-700' },
  CANCELLED: { label: 'Cancelado',  className: 'bg-red-100 text-red-700' },
};

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    getOrder(Number(id))
      .then(setOrder)
      .catch(() => navigate('/orders'))
      .finally(() => setLoading(false));
  }, [id, navigate]);

  if (loading) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-10 space-y-3 animate-pulse">
        <div className="h-6 bg-gray-100 rounded w-1/4" />
        <div className="h-32 bg-gray-100 rounded-xl" />
        <div className="h-48 bg-gray-100 rounded-xl" />
      </div>
    );
  }

  if (!order) return null;

  const { label, className } = statusConfig[order.status];

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8">
      <Link to="/orders" className="inline-flex items-center gap-2 text-sm text-gray-500 hover:text-gray-700 mb-6">
        <ArrowLeft size={16} /> Meus pedidos
      </Link>

      <div className="flex items-start justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Pedido #{order.id}</h1>
          <p className="text-gray-500 text-sm mt-1">
            {new Date(order.createdAt).toLocaleString('pt-BR')}
          </p>
        </div>
        <span className={`text-sm font-medium px-3 py-1 rounded-full ${className}`}>{label}</span>
      </div>

      {/* Delivery address */}
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

      {/* Items */}
      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden mb-4">
        <div className="px-4 py-3 border-b border-gray-100">
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">Itens</p>
        </div>
        {order.items.map(item => (
          <div key={item.id} className="flex items-center justify-between px-4 py-3 border-b border-gray-50 last:border-0">
            <div>
              <p className="text-sm font-medium text-gray-900">{item.productName}</p>
              <p className="text-xs text-gray-500">
                {item.quantity}x {item.unitPrice.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
              </p>
            </div>
            <span className="text-sm font-semibold text-gray-700">
              {item.subtotal.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
            </span>
          </div>
        ))}
        <div className="px-4 py-3 bg-gray-50 flex justify-between font-bold">
          <span>Total</span>
          <span className="text-indigo-600 text-lg">
            {order.total.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
          </span>
        </div>
      </div>

      <Link
        to="/"
        className="inline-block px-5 py-2.5 border border-gray-300 text-gray-700 hover:bg-gray-50 rounded-xl text-sm font-medium transition-colors"
      >
        Continuar comprando
      </Link>
    </div>
  );
}
