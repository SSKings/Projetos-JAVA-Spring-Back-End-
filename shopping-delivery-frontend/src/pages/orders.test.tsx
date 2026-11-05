import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import OrdersPage from './OrdersPage';
import OrderDetailPage from './OrderDetailPage';
import * as ordersApi from '../api/orders';
import { order } from '../test/fixtures';

vi.mock('../api/orders');

const renderAt = (path: string) => render(
  <MemoryRouter initialEntries={[path]}>
    <Routes>
      <Route path="/orders" element={<OrdersPage />} />
      <Route path="/orders/:id" element={<OrderDetailPage />} />
    </Routes>
  </MemoryRouter>,
);

beforeEach(() => vi.resetAllMocks());

describe('OrdersPage', () => {
  it('lista pedidos com status traduzido', async () => {
    vi.mocked(ordersApi.getOrders).mockResolvedValue([order(), order({ id: 4, status: 'DELIVERED', items: [] })]);
    renderAt('/orders');
    expect(await screen.findByText('Pedido #3')).toBeInTheDocument();
    expect(screen.getByText('Entregue')).toBeInTheDocument();
    expect(screen.getByText('1 item')).toBeInTheDocument();
    expect(screen.getByText('0 itens')).toBeInTheDocument();
  });

  it('estado vazio', async () => {
    vi.mocked(ordersApi.getOrders).mockResolvedValue([]);
    renderAt('/orders');
    expect(await screen.findByText('Nenhum pedido ainda')).toBeInTheDocument();
  });
});

describe('OrderDetailPage', () => {
  it('mostra itens, endereço e status', async () => {
    vi.mocked(ordersApi.getOrder).mockResolvedValue(order({ status: 'SHIPPED' }));
    renderAt('/orders/3');
    expect(await screen.findByText('Pedido #3')).toBeInTheDocument();
    expect(screen.getByText('Enviado')).toBeInTheDocument();
    expect(screen.getByText(/Rua A, 10/)).toBeInTheDocument();
    expect(ordersApi.getOrder).toHaveBeenCalledWith(3);
  });

  it('pedido inexistente volta para a lista', async () => {
    vi.mocked(ordersApi.getOrder).mockRejectedValue(new Error('404'));
    vi.mocked(ordersApi.getOrders).mockResolvedValue([]);
    renderAt('/orders/99');
    expect(await screen.findByText('Nenhum pedido ainda')).toBeInTheDocument();
  });
});
