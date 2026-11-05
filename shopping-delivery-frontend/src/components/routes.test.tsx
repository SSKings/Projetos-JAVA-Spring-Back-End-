import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
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

  it('libera para admin', () => {
    loginAs('ADMIN');
    renderWithProviders(<AdminRoute><div>ADMIN AREA</div></AdminRoute>);
    expect(screen.getByText('ADMIN AREA')).toBeInTheDocument();
  });
});
