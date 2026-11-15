import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import App from './App';
import * as productsApi from './api/products';
import * as cartApi from './api/cart';
import * as adminApi from './api/admin';
import * as ordersApi from './api/orders';
import { loginAs, page } from './test/utils';
import { product } from './test/fixtures';

vi.mock('./api/products');
vi.mock('./api/cart');
vi.mock('./api/admin');
vi.mock('./api/orders');

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

  it('/account leva o cliente para "Minha conta" na aba de pedidos', async () => {
    loginAs('CUSTOMER', { name: 'Ana Souza', email: 'ana@a.com' });
    vi.mocked(ordersApi.getOrders).mockResolvedValue([]);
    window.history.pushState({}, '', '/account');
    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Minha conta' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/orders');
    expect(await screen.findByText('Nenhum pedido ainda')).toBeInTheDocument();
  });

  it('visitante que abre o histórico de compras vai para o login', async () => {
    window.history.pushState({}, '', '/purchases');
    render(<App />);
    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument();
    expect(screen.queryByText('Minha conta')).not.toBeInTheDocument();
  });
});
