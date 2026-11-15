import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Package, ChevronRight, Search } from 'lucide-react';
import { getOrders } from '../../api/orders';
import Pagination from '../../components/Pagination';
import { formatBRL, formatDate, getErrorMessage, orderStatusConfig } from '../../utils/format';
import { filterOrders, isAwaitingPayment, itemsPreview, matchesFilter, summarizeOrders, type OrderFilter } from '../../utils/orders';
import type { Order } from '../../types';

const ORDERS_PAGE_SIZE = 5;

const filters: { value: OrderFilter; label: string }[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'IN_PROGRESS', label: 'Em andamento' },
  { value: 'DELIVERED', label: 'Entregues' },
  { value: 'CANCELLED', label: 'Cancelados' },
];

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState<OrderFilter>('ALL');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);

  const fetchOrders = useCallback(() => {
    getOrders()
      .then(setOrders)
      .catch(err => setError(getErrorMessage(err, 'Não foi possível carregar seus pedidos.')))
      .finally(() => setLoading(false));
  }, []);

  useEffect(fetchOrders, [fetchOrders]);

  const retry = () => {
    setLoading(true);
    setError('');
    fetchOrders();
  };

  const summary = useMemo(() => summarizeOrders(orders), [orders]);
  const visible = useMemo(() => filterOrders(orders, filter, query), [orders, filter, query]);
  const totalPages = Math.ceil(visible.length / ORDERS_PAGE_SIZE);
  const pageItems = visible.slice(page * ORDERS_PAGE_SIZE, (page + 1) * ORDERS_PAGE_SIZE);

  const changeFilter = (f: OrderFilter) => { setFilter(f); setPage(0); };
  const changeQuery = (q: string) => { setQuery(q); setPage(0); };

  if (loading) {
    return (
      <div className="space-y-3" aria-busy="true">
        {[1, 2, 3].map(i => <div key={i} className="h-24 bg-gray-100 rounded-xl animate-pulse" />)}
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-16">
        <p className="text-red-600 bg-red-50 inline-block px-4 py-2 rounded-lg text-sm">{error}</p>
        <div>
          <button onClick={retry} className="mt-4 px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-sm font-medium">
            Tentar novamente
          </button>
        </div>
      </div>
    );
  }

  if (orders.length === 0) {
    return (
      <div className="text-center py-20">
        <Package size={64} className="mx-auto text-gray-200 mb-4" />
        <h2 className="text-xl font-bold text-gray-700">Nenhum pedido ainda</h2>
        <p className="text-gray-400 mt-2">Suas compras aparecerão aqui</p>
        <Link to="/" className="inline-block mt-6 px-6 py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold transition-colors">
          Começar a Comprar
        </Link>
      </div>
    );
  }

  const count = (f: OrderFilter) => orders.filter(o => matchesFilter(o.status, f)).length;

  return (
    <div>
      {/* Resumo */}
      <dl className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
        {[
          { label: 'Pedidos', value: String(summary.count) },
          { label: 'Total gasto', value: formatBRL(summary.totalSpent) },
          { label: 'Em andamento', value: String(summary.inProgress) },
          { label: 'Entregues', value: String(summary.delivered) },
        ].map(s => (
          <div key={s.label} className="bg-white rounded-xl border border-gray-200 p-4">
            <dt className="text-xs text-gray-500">{s.label}</dt>
            <dd className="text-lg font-bold text-gray-900 mt-1">{s.value}</dd>
          </div>
        ))}
      </dl>

      {/* Filtros */}
      <div className="flex flex-col sm:flex-row sm:items-center gap-3 mb-4">
        <div role="group" aria-label="Filtrar por status" className="flex gap-2 overflow-x-auto">
          {filters.map(f => (
            <button
              key={f.value}
              onClick={() => changeFilter(f.value)}
              aria-pressed={filter === f.value}
              className={`px-3 py-1.5 rounded-full text-sm whitespace-nowrap border transition-colors ${
                filter === f.value ? 'bg-indigo-600 border-indigo-600 text-white' : 'bg-white border-gray-200 text-gray-600 hover:border-gray-300'
              }`}
            >
              {f.label} ({count(f.value)})
            </button>
          ))}
        </div>
        <div className="relative sm:ml-auto sm:w-64">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="search"
            aria-label="Buscar pedidos"
            placeholder="Nº do pedido ou produto"
            value={query}
            onChange={e => changeQuery(e.target.value)}
            className="w-full pl-9 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>
      </div>

      {visible.length === 0 ? (
        <div className="text-center py-12 bg-white rounded-xl border border-gray-200">
          <p className="text-gray-500">Nenhum pedido encontrado com esses filtros.</p>
          <button onClick={() => { changeFilter('ALL'); changeQuery(''); }} className="mt-3 text-sm text-indigo-600 hover:underline">
            Limpar filtros
          </button>
        </div>
      ) : (
        <ul className="space-y-3">
          {pageItems.map(order => {
            const { label, className } = orderStatusConfig[order.status];
            return (
              <li key={order.id}>
                <Link
                  to={`/orders/${order.id}`}
                  className="block bg-white rounded-xl border border-gray-200 hover:shadow-md transition-shadow p-4"
                >
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <div className="flex items-center gap-3 mb-1">
                        <span className="font-semibold text-gray-900">Pedido #{order.id}</span>
                        <span className={`text-xs font-medium px-2.5 py-0.5 rounded-full ${className}`}>{label}</span>
                        {isAwaitingPayment(order) && (
                          <span className="text-xs font-medium px-2.5 py-0.5 rounded-full bg-yellow-50 text-yellow-700 border border-yellow-200">
                            Aguardando Pix
                          </span>
                        )}
                      </div>
                      <p className="text-sm text-gray-500">{formatDate(order.createdAt)}</p>
                      <p className="text-xs text-gray-400 mt-0.5 truncate">
                        {order.items.length} {order.items.length === 1 ? 'item' : 'itens'}
                        {order.items.length > 0 && <> · {itemsPreview(order)}</>}
                      </p>
                    </div>
                    <div className="flex items-center gap-3 shrink-0">
                      <span className="font-bold text-indigo-600">{formatBRL(order.total)}</span>
                      <ChevronRight size={18} className="text-gray-400" />
                    </div>
                  </div>
                </Link>
              </li>
            );
          })}
        </ul>
      )}

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />
    </div>
  );
}
