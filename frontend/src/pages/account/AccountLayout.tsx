import { NavLink, Outlet } from 'react-router-dom';
import { History, KeyRound, MapPin, ShieldCheck, ShoppingBag } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';

const links = [
  { to: '/orders',    label: 'Meus pedidos',         icon: ShoppingBag },
  { to: '/purchases', label: 'Histórico de compras', icon: History },
  { to: '/addresses', label: 'Endereços',            icon: MapPin },
  { to: '/privacy',   label: 'Dados e privacidade',  icon: ShieldCheck },
  { to: '/security',  label: 'Segurança',            icon: KeyRound },
];

/** Área do cliente: pedidos, produtos já comprados e endereços de entrega. */
export default function AccountLayout() {
  const { user } = useAuth();
  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Minha conta</h1>
        {user && <p className="text-gray-500 mt-1">{user.name} · {user.email}</p>}
      </div>

      <nav aria-label="Minha conta" className="flex gap-1 overflow-x-auto border-b border-gray-200 mb-6">
        {links.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `flex items-center gap-2 px-4 py-2.5 text-sm font-medium whitespace-nowrap border-b-2 -mb-px transition-colors ${
                isActive ? 'border-indigo-600 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-800'
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
