import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import CartPage from './CartPage';
import CheckoutPage from './checkout/CheckoutPage';
import * as addressesApi from '../api/addresses';
import * as ordersApi from '../api/orders';
import { address, cart, order } from '../test/fixtures';
import { mockCart } from '../test/cartMock';

vi.mock('../api/addresses');
vi.mock('../api/orders');
vi.mock('../contexts/CartContext', () => ({ useCart: vi.fn() }));

const renderAt = (path: string) => render(
  <MemoryRouter initialEntries={[path]}>
    <Routes>
      <Route path="/cart" element={<CartPage />} />
      <Route path="/checkout" element={<CheckoutPage />} />
      <Route path="/orders" element={<div>ORDERS PAGE</div>} />
      <Route path="/" element={<div>HOME PAGE</div>} />
    </Routes>
  </MemoryRouter>,
);

beforeEach(() => vi.resetAllMocks());

describe('CartPage', () => {
  it('carrinho vazio', () => {
    mockCart({ items: [], total: 0, itemCount: 0 });
    renderAt('/cart');
    expect(screen.getByText('Seu carrinho está vazio')).toBeInTheDocument();
  });

  it('carregando', () => {
    mockCart(cart(), true);
    renderAt('/cart');
    expect(screen.getByText('Carregando carrinho...')).toBeInTheDocument();
  });

  it('altera quantidade, remove e vai para o checkout', async () => {
    const cartApi = mockCart(cart());
    vi.mocked(addressesApi.getAddresses).mockResolvedValue([]);
    renderAt('/cart');

    expect(screen.getByText('2 itens')).toBeInTheDocument();
    const buttons = screen.getAllByRole('button');
    await userEvent.click(buttons[0]); // −
    expect(cartApi.updateItem).toHaveBeenCalledWith(11, 1);
    await userEvent.click(buttons[1]); // +
    expect(cartApi.updateItem).toHaveBeenCalledWith(11, 3);
    await userEvent.click(buttons[2]); // lixeira
    expect(cartApi.removeItem).toHaveBeenCalledWith(11);

    await userEvent.click(screen.getByRole('button', { name: /Finalizar Compra/ }));
    expect(await screen.findByText('Escolha o endereço de entrega')).toBeInTheDocument();
  });
});

describe('CheckoutPage', () => {
  it('fluxo completo: endereço existente → revisão → confirmação', async () => {
    const cartApi = mockCart(cart());
    vi.mocked(addressesApi.getAddresses).mockResolvedValue([address()]);
    vi.mocked(ordersApi.checkout).mockResolvedValue(order({ id: 42 }));
    renderAt('/checkout');

    expect(await screen.findByRole('radio')).toBeChecked();
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    expect(screen.getByText('Revisar pedido')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Alterar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar Pedido' }));

    expect(await screen.findByText('Pedido confirmado!')).toBeInTheDocument();
    expect(screen.getByText('#42')).toBeInTheDocument();
    expect(ordersApi.checkout).toHaveBeenCalledWith(7);
    expect(cartApi.clearCart).toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Ver Meus Pedidos' }));
    expect(screen.getByText('ORDERS PAGE')).toBeInTheDocument();
  });

  it('cadastra novo endereço e o seleciona', async () => {
    mockCart(cart());
    vi.mocked(addressesApi.getAddresses).mockResolvedValue([]);
    vi.mocked(addressesApi.createAddress).mockResolvedValue(address({ id: 99, street: 'Rua Nova' }));
    renderAt('/checkout');

    expect(screen.getByRole('button', { name: 'Continuar' })).toBeDisabled();
    await userEvent.click(await screen.findByRole('button', { name: /Adicionar novo endereço/ }));
    const form = screen.getByText('Novo endereço').closest('form')!;
    const f = within(form);
    await userEvent.type(f.getByPlaceholderText('00000-000'), '01000-000');
    await userEvent.type(f.getByPlaceholderText('Rua das Flores'), 'Rua Nova');
    await userEvent.type(f.getByPlaceholderText('123'), '5');
    await userEvent.type(f.getByPlaceholderText('Centro'), 'Centro');
    await userEvent.type(f.getByPlaceholderText('São Paulo'), 'SP');
    await userEvent.type(f.getByPlaceholderText('SP'), 'SP');
    await userEvent.click(f.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText(/Rua Nova/)).toBeInTheDocument();
    expect(screen.getByRole('radio')).toBeChecked();
    expect(screen.getByRole('button', { name: 'Continuar' })).toBeEnabled();
  });

  it('erros ao salvar endereço e ao finalizar são exibidos', async () => {
    mockCart(cart());
    vi.mocked(addressesApi.getAddresses).mockResolvedValue([address()]);
    vi.mocked(addressesApi.createAddress).mockRejectedValue(new Error('x'));
    vi.mocked(ordersApi.checkout).mockRejectedValue(new Error('x'));
    renderAt('/checkout');

    await userEvent.click(await screen.findByRole('button', { name: /Adicionar novo endereço/ }));
    const form = screen.getByText('Novo endereço').closest('form')!;
    for (const input of within(form).getAllByRole('textbox')) await userEvent.type(input, 'SP');
    await userEvent.click(within(form).getByRole('button', { name: 'Salvar' }));
    expect(await screen.findByText('Erro ao salvar endereço.')).toBeInTheDocument();
    await userEvent.click(within(form).getByRole('button', { name: 'Cancelar' }));

    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar Pedido' }));
    expect(await screen.findByText(/Erro ao finalizar pedido/)).toBeInTheDocument();
  });

  it('carrinho vazio redireciona para o carrinho', async () => {
    mockCart({ items: [], total: 0, itemCount: 0 });
    vi.mocked(addressesApi.getAddresses).mockResolvedValue([]);
    renderAt('/checkout');
    expect(await screen.findByText('Seu carrinho está vazio')).toBeInTheDocument();
  });
});
