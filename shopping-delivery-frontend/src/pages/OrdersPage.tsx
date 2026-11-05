import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Package, ChevronRight } from 'lucide-react';
import { getOrders } from '../api/orders';
import type { Order, OrderStatus } from '../types';

const statusConfig: Record<OrderStatus, { label: string; className: string }> = {
  PENDING:   { label: 'Pendente',    className: 'bg-yellow-100 text-yellow-700' },
  CONFIRMED: { label: 'Confirmado',  className: 'bg-blue-100 text-blue-700' },
  SHIPPED:   { label: 'Enviado',     className: 'bg-purple-100 text-purple-700' },
  DELIVERED: { label: 'Entregue',    className: 'bg-green-100 text-green-700' },
  CANCELLED: { label: 'Cancelado',   className: 'bg-red-100 text-red-700' },
};

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getOrders().then(setOrders).finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-10 space-y-3">
        {[1, 2, 3].map(i => (
          <div key={i} className="h-24 bg-gray-100 rounded-xl animate-pulse" />
        ))}
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Meus Pedidos</h1>
        <p className="text-gray-500 mt-1">Histórico completo das suas compras</p>
      </div>

      {orders.length === 0 ? (
        <div className="text-center py-20">
          <Package size={64} className="mx-auto text-gray-200 mb-4" />
          <h2 className="text-xl font-bold text-gray-700">Nenhum pedido ainda</h2>
          <p className="text-gray-400 mt-2">Suas compras aparecerão aqui</p>
          <Link
            to="/"
            className="inline-block mt-6 px-6 py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold transition-colors"
          >
            Começar a Comprar
          </Link>
        </div>
      ) : (
        <div className="space-y-3">
          {orders.map(order => {
            const { label, className } = statusConfig[order.status];
            return (
              <Link
                key={order.id}
                to={`/orders/${order.id}`}
                className="block bg-white rounded-xl border border-gray-200 hover:shadow-md transition-shadow p-4"
              >
                <div className="flex items-center justify-between">
                  <div>
                    <div className="flex items-center gap-3 mb-1">
                      <span className="font-semibold text-gray-900">Pedido #{order.id}</span>
                      <span className={`text-xs font-medium px-2.5 py-0.5 rounded-full ${className}`}>
                        {label}
                      </span>
                    </div>
                    <p className="text-sm text-gray-500">
                      {new Date(order.createdAt).toLocaleDateString('pt-BR', {
                        day: '2-digit', month: 'long', year: 'numeric'
                      })}
                    </p>
                    <p className="text-xs text-gray-400 mt-0.5">
                      {order.items.length} {order.items.length === 1 ? 'item' : 'itens'}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="font-bold text-indigo-600">
                      {order.total.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
                    </span>
                    <ChevronRight size={18} className="text-gray-400" />
                  </div>
                </div>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}
