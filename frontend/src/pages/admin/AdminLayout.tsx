import { NavLink, Outlet } from 'react-router-dom';
import { LayoutDashboard, Package, ScrollText, ShoppingBag, Users } from 'lucide-react';

const links = [
  { to: '/admin',          label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/admin/products', label: 'Produtos',  icon: Package,         end: false },
  { to: '/admin/orders',   label: 'Pedidos',   icon: ShoppingBag,     end: false },
  { to: '/admin/users',    label: 'Usuários',  icon: Users,           end: false },
  { to: '/admin/audit',    label: 'Auditoria', icon: ScrollText,      end: false },
];

export default function AdminLayout() {
  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Área administrativa</h1>
        <p className="text-gray-500 mt-1">Gerencie produtos, pedidos e clientes da loja</p>
      </div>

      <nav className="flex gap-1 overflow-x-auto border-b border-gray-200 mb-6">
        {links.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              `flex items-center gap-2 px-4 py-2.5 text-sm font-medium whitespace-nowrap border-b-2 -mb-px transition-colors ${
                isActive
                  ? 'border-indigo-600 text-indigo-600'
                  : 'border-transparent text-gray-500 hover:text-gray-800'
              }`
            }
          >
            <Icon size={16} />
            {label}
          </NavLink>
        ))}
      </nav>

      <Outlet />
    </div>
  );
}
