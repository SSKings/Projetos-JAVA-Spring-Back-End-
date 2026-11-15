import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getDashboard } from '../../api/admin';
import type { DashboardStats } from '../../types';
import { formatBRL, getErrorMessage } from '../../utils/format';

export default function AdminDashboardPage() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    getDashboard().then(setStats).catch(err => setError(getErrorMessage(err)));
  }, []);

  if (error) return <div className="px-4 py-3 bg-red-50 text-red-700 rounded-lg text-sm">{error}</div>;
  if (!stats) {
    return (
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {[1, 2, 3, 4].map(i => <div key={i} className="h-28 bg-gray-100 rounded-xl animate-pulse" />)}
      </div>
    );
  }

  const cards = [
    { label: 'Faturamento', value: formatBRL(stats.revenue), hint: 'Pedidos pagos e não cancelados', to: '/admin/orders' },
    { label: 'Pedidos', value: stats.totalOrders, hint: `${stats.pendingOrders} pendente(s)`, to: '/admin/orders' },
    { label: 'Clientes', value: stats.totalCustomers, hint: 'Contas de compradores', to: '/admin/users' },
    { label: 'Produtos à venda', value: stats.activeProducts, hint: `${stats.totalProducts} cadastrados`, to: '/admin/products' },
  ];

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {cards.map(c => (
          <Link key={c.label} to={c.to}
            className="bg-white rounded-xl border border-gray-200 p-5 hover:shadow-md transition-shadow">
            <p className="text-sm text-gray-500">{c.label}</p>
            <p className="text-2xl font-bold text-gray-900 mt-1">{c.value}</p>
            <p className="text-xs text-gray-400 mt-1">{c.hint}</p>
          </Link>
        ))}
      </div>

      {stats.lowStockProducts > 0 && (
        <div className="px-4 py-3 bg-amber-50 text-amber-800 border border-amber-200 rounded-lg text-sm">
          {stats.lowStockProducts} produto(s) ativo(s) com 5 unidades ou menos em estoque.{' '}
          <Link to="/admin/products" className="font-medium underline">Ver produtos</Link>
        </div>
      )}
    </div>
  );
}
