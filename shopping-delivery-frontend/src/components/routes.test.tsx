import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../contexts/AuthContext';
import AdminRoute from './AdminRoute';
import ProtectedRoute from './ProtectedRoute';
import { loginAs, renderWithProviders } from '../test/utils';

describe('ProtectedRoute', () => {
  it('redireciona visitante para /login', () => {
    renderWithProviders(<ProtectedRoute><div>SECRET</div></ProtectedRoute>);
    expect(screen.getByText('LOGIN PAGE')).toBeInTheDocument();
  });

  it('mostra conteúdo para usuário logado', () => {
    loginAs('CUSTOMER');
    renderWithProviders(<ProtectedRoute><div>SECRET</div></ProtectedRoute>);
    expect(screen.getByText('SECRET')).toBeInTheDocument();
  });
});

describe('AdminRoute', () => {
  it('redireciona visitante para /login', () => {
    renderWithProviders(<AdminRoute><div>ADMIN AREA</div></AdminRoute>);
    expect(screen.getByText('LOGIN PAGE')).toBeInTheDocument();
  });

  it('redireciona cliente comum para a home', () => {
    loginAs('CUSTOMER');
    renderWithProviders(<AdminRoute><div>ADMIN AREA</div></AdminRoute>);
    expect(screen.getByText('HOME PAGE')).toBeInTheDocument();
  });

  it('admin sem MFA (obrigatório) vai para a tela de segurança', () => {
    loginAs('ADMIN');
    localStorage.setItem('user', JSON.stringify({ name: 'A', email: 'a@x.com', role: 'ADMIN', mfaSetupRequired: true }));
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <AuthProvider>
          <Routes>
            <Route path="/admin" element={<AdminRoute><div>ADMIN AREA</div></AdminRoute>} />
            <Route path="/security" element={<div>SECURITY PAGE</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );
    expect(screen.getByText('SECURITY PAGE')).toBeInTheDocument();
    expect(screen.queryByText('ADMIN AREA')).not.toBeInTheDocument();
  });

  it('libera para admin', () => {
    loginAs('ADMIN');
    renderWithProviders(<AdminRoute><div>ADMIN AREA</div></AdminRoute>);
    expect(screen.getByText('ADMIN AREA')).toBeInTheDocument();
  });
});
