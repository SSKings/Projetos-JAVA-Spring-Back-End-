import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { History } from 'lucide-react';
import { getOrders } from '../../api/orders';
import { formatBRL, formatDate, getErrorMessage } from '../../utils/format';
import { aggregatePurchases, sortPurchases, type PurchaseSort } from '../../utils/orders';
import type { Order } from '../../types';

/** Produtos já comprados, consolidados a partir dos pedidos não cancelados. */
export default function PurchaseHistoryPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [sort, setSort] = useState<PurchaseSort>('recent');

  useEffect(() => {
    getOrders()
      .then(setOrders)
      .catch(err => setError(getErrorMessage(err, 'Não foi possível carregar seu histórico.')))
      .finally(() => setLoading(false));
  }, []);

  const purchases = useMemo(() => aggregatePurchases(orders), [orders]);
  const sorted = useMemo(() => sortPurchases(purchases, sort), [purchases, sort]);
  const units = purchases.reduce((n, p) => n + p.quantity, 0);
  const spent = purchases.reduce((n, p) => n + p.totalSpent, 0);

  if (loading) {
    return <div className="h-48 bg-gray-100 rounded-xl animate-pulse" aria-busy="true" />;
  }

  if (error) {
    return <p className="text-sm text-red-600 bg-red-50 px-4 py-3 rounded-lg">{error}</p>;
  }

  if (purchases.length === 0) {
    return (
      <div className="text-center py-20">
        <History size={64} className="mx-auto text-gray-200 mb-4" />
        <h2 className="text-xl font-bold text-gray-700">Você ainda não comprou nenhum produto</h2>
        <p className="text-gray-400 mt-2">Pedidos cancelados não entram no histórico de compras</p>
        <Link to="/" className="inline-block mt-6 px-6 py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold">
          Ver produtos
        </Link>
      </div>
    );
  }

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-3 mb-4">
        <p className="text-sm text-gray-600">
          <strong>{purchases.length}</strong> {purchases.length === 1 ? 'produto' : 'produtos'} ·{' '}
          <strong>{units}</strong> {units === 1 ? 'unidade' : 'unidades'} · <strong>{formatBRL(spent)}</strong> em compras
        </p>
        <label className="text-sm text-gray-600 flex items-center gap-2">
          Ordenar por
          <select
            value={sort}
            onChange={e => setSort(e.target.value as PurchaseSort)}
            className="border border-gray-300 rounded-lg px-2 py-1.5 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="recent">Compra mais recente</option>
            <option value="quantity">Mais comprados</option>
            <option value="spent">Maior gasto</option>
          </select>
        </label>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-xs text-gray-500 uppercase tracking-wide">
            <tr>
              <th className="text-left px-4 py-3 font-semibold">Produto</th>
              <th className="text-right px-4 py-3 font-semibold">Qtd.</th>
              <th className="text-right px-4 py-3 font-semibold">Total gasto</th>
              <th className="text-left px-4 py-3 font-semibold">Última compra</th>
            </tr>
          </thead>
          <tbody>
            {sorted.map(p => (
              <tr key={p.productId} className="border-t border-gray-100">
                <td className="px-4 py-3">
                  <Link to={`/products/${p.productId}`} className="font-medium text-gray-900 hover:text-indigo-600">
                    {p.productName}
                  </Link>
                  <p className="text-xs text-gray-400">
                    {p.orderCount} {p.orderCount === 1 ? 'pedido' : 'pedidos'} · último preço {formatBRL(p.lastUnitPrice)}
                  </p>
                </td>
                <td className="px-4 py-3 text-right">{p.quantity}</td>
                <td className="px-4 py-3 text-right font-semibold">{formatBRL(p.totalSpent)}</td>
                <td className="px-4 py-3 whitespace-nowrap">
                  {formatDate(p.lastPurchaseAt)}{' '}
                  <Link to={`/orders/${p.lastOrderId}`} className="text-indigo-600 hover:underline">#{p.lastOrderId}</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
