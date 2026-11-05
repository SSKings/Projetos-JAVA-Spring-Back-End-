import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Navbar from './Navbar';
import { loginAs, renderWithProviders } from '../test/utils';
import { cart } from '../test/fixtures';
import { mockCart } from '../test/cartMock';

vi.mock('../contexts/CartContext', () => ({ useCart: vi.fn() }));

beforeEach(() => mockCart(cart({ itemCount: 12 })));

describe('Navbar', () => {
  it('visitante vê Entrar/Cadastrar', () => {
    renderWithProviders(<Navbar />);
    expect(screen.getByText('Entrar')).toBeInTheDocument();
    expect(screen.getByText('Cadastrar')).toBeInTheDocument();
    expect(screen.queryByText('Admin')).not.toBeInTheDocument();
  });

  it('cliente vê carrinho (9+) mas não o link Admin', () => {
    loginAs('CUSTOMER', { name: 'Ana Souza', email: 'ana@a.com' });
    renderWithProviders(<Navbar />);
    expect(screen.getByText('9+')).toBeInTheDocument();
    expect(screen.getByText('Ana')).toBeInTheDocument();
    expect(screen.queryByText('Admin')).not.toBeInTheDocument();
  });

  it('admin vê o link Admin; sair encerra a sessão', async () => {
    loginAs('ADMIN', { name: 'Chefe', email: 'admin@x.com' });
    renderWithProviders(<Navbar />);
    expect(screen.getByRole('link', { name: 'Admin' })).toHaveAttribute('href', '/admin');

    await userEvent.click(screen.getByRole('button', { name: /Sair/ }));
    expect(localStorage.getItem('token')).toBeNull();
    expect(screen.getByText('HOME PAGE')).toBeInTheDocument();
  });
});
