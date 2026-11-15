import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../contexts/AuthProvider';
import type { Role } from '../types';

/** Simula uma sessão salva no localStorage antes de montar o AuthProvider. */
export function loginAs(role: Role, user = { name: 'Admin Teste', email: 'admin@x.com' }) {
  localStorage.setItem('token', 'tok');
  localStorage.setItem('user', JSON.stringify({ ...user, role }));
}

/** Renderiza dentro de AuthProvider + MemoryRouter; rotas extras ajudam a verificar redirecionamentos. */
export function renderWithProviders(ui: ReactElement, { route = '/test', path = '*' } = {}) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <Routes>
          <Route path={path} element={ui} />
          <Route path="/login" element={<div>LOGIN PAGE</div>} />
          <Route path="/" element={<div>HOME PAGE</div>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

export const page = <T,>(content: T[], number = 0, totalPages = 1) => ({
  content, number, totalPages, size: 10, totalElements: content.length,
});
