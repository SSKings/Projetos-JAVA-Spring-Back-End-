import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import App from './App';
import * as productsApi from './api/products';
import * as cartApi from './api/cart';
import * as adminApi from './api/admin';
import { loginAs, page } from './test/utils';
import { product } from './test/fixtures';

vi.mock('./api/products');
vi.mock('./api/cart');
vi.mock('./api/admin');

describe('App (rotas)', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(productsApi.getProducts).mockResolvedValue(page([product()]));
    vi.mocked(cartApi.getCart).mockResolvedValue({ items: [], total: 0, itemCount: 0 });
  });

  it('home lista produtos', async () => {
    window.history.pushState({}, '', '/');
    render(<App />);
    expect(await screen.findByText('Nossos Produtos')).toBeInTheDocument();
  });

  it('admin acessa /admin com as abas da área administrativa', async () => {
    loginAs('ADMIN');
    vi.mocked(adminApi.getDashboard).mockResolvedValue({
      totalProducts: 1, activeProducts: 1, lowStockProducts: 0, totalCustomers: 0, totalOrders: 0, pendingOrders: 0, revenue: 0,
    });
    window.history.pushState({}, '', '/admin');
    render(<App />);

    expect(await screen.findByText('Área administrativa')).toBeInTheDocument();
    for (const tab of ['Dashboard', 'Produtos', 'Pedidos', 'Usuários']) {
      expect(screen.getByRole('link', { name: tab })).toBeInTheDocument();
    }
  });

  it('cliente que tenta abrir /admin volta para a loja', async () => {
    loginAs('CUSTOMER');
    window.history.pushState({}, '', '/admin/users');
    render(<App />);
    expect(await screen.findByText('Nossos Produtos')).toBeInTheDocument();
    expect(screen.queryByText('Área administrativa')).not.toBeInTheDocument();
  });
});
