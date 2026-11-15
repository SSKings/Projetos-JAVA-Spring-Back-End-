import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import CartPage from './CartPage';
import CheckoutPage from './checkout/CheckoutPage';
import * as addressesApi from '../api/addresses';
import * as ordersApi from '../api/orders';
import { PIX_CODE, address, cart, order, payment } from '../test/fixtures';
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
      <Route path="/orders/42" element={<div>ORDER 42</div>} />
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

  it('não passa de 10 unidades por produto (limite do back-end)', async () => {
    const cartApi = mockCart(cart({ items: [{ id: 11, productId: 1, productName: 'Caneca', productImageUrl: 'http://img',
      unitPrice: 29.9, quantity: 10, subtotal: 299 }], itemCount: 10, total: 299 }));
    renderAt('/cart');
    const plus = screen.getAllByRole('button')[1];
    expect(plus).toBeDisabled();
    expect(plus).toHaveAttribute('title', 'Limite de 10 unidades por produto');
    await userEvent.click(plus);
    expect(cartApi.updateItem).not.toHaveBeenCalled();
  });
});

describe('CheckoutPage', () => {
  it('fluxo completo: endereço → revisão (Pix) → pagamento → pago → confirmação', async () => {
    const cartApi = mockCart(cart());
    vi.mocked(addressesApi.getAddresses).mockResolvedValue([address()]);
    vi.mocked(ordersApi.checkout).mockResolvedValue(order({ id: 42, payment: payment() }));
    vi.mocked(ordersApi.getPayment).mockResolvedValue(payment({ status: 'PAID', pixCopyPaste: null, expiresInSeconds: 0 }));
    renderAt('/checkout');

    expect(await screen.findByRole('radio', { name: /Rua A/ })).toBeChecked();
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    expect(screen.getByText('Revisar pedido')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /Pix/ })).toBeChecked();

    await userEvent.click(screen.getByRole('button', { name: 'Alterar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar e pagar com Pix' }));

    // etapa 3: Pix
    expect(await screen.findByText('Pague com Pix para concluir')).toBeInTheDocument();
    expect(ordersApi.checkout).toHaveBeenCalledWith(7, 'PIX');
    expect(cartApi.clearCart).toHaveBeenCalled();
    expect(screen.getByLabelText('Código Pix copia e cola')).toHaveValue(PIX_CODE);

    // polling detecta o pagamento → etapa 4 (o PixPayment consulta a cada 5 s)
    expect(await screen.findByText('Pedido confirmado!', {}, { timeout: 7000 })).toBeInTheDocument();
    expect(screen.getByText('#42')).toBeInTheDocument();
    expect(screen.getByText(/pago e confirmado/)).toBeInTheDocument();
    expect(ordersApi.getPayment).toHaveBeenCalledWith(42);

    await userEvent.click(screen.getByRole('button', { name: 'Ver Meus Pedidos' }));
    expect(screen.getByText('ORDERS PAGE')).toBeInTheDocument();
  }, 10_000);

  it('pagar depois leva ao detalhe do pedido; Pix expirado oferece ver os pedidos', async () => {
    mockCart(cart());
    vi.mocked(addressesApi.getAddresses).mockResolvedValue([address()]);
    vi.mocked(ordersApi.checkout).mockResolvedValue(order({ id: 42, payment: payment() }));
    renderAt('/checkout');

    await userEvent.click(await screen.findByRole('button', { name: 'Continuar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar e pagar com Pix' }));
    await userEvent.click(await screen.findByRole('button', { name: /Pagar depois/ }));
    expect(screen.getByText('ORDER 42')).toBeInTheDocument();
  });

  it('Pix vence durante a etapa de pagamento', async () => {
    mockCart(cart());
    vi.mocked(addressesApi.getAddresses).mockResolvedValue([address()]);
    vi.mocked(ordersApi.checkout).mockResolvedValue(order({ id: 42, payment: payment() }));
    vi.mocked(ordersApi.getPayment).mockResolvedValue(payment({ status: 'EXPIRED', pixCopyPaste: null, expiresInSeconds: 0 }));
    renderAt('/checkout');

    await userEvent.click(await screen.findByRole('button', { name: 'Continuar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar e pagar com Pix' }));
    expect(await screen.findByText(/prazo para pagamento acabou/, {}, { timeout: 7000 })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Ver Meus Pedidos' }));
    expect(screen.getByText('ORDERS PAGE')).toBeInTheDocument();
  }, 10_000);

  it('pedido sem cobrança vai direto para a confirmação', async () => {
    mockCart(cart());
    vi.mocked(addressesApi.getAddresses).mockResolvedValue([address()]);
    vi.mocked(ordersApi.checkout).mockResolvedValue(order({ id: 43, payment: null }));
    renderAt('/checkout');

    await userEvent.click(await screen.findByRole('button', { name: 'Continuar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar e pagar com Pix' }));
    expect(await screen.findByText('Pedido confirmado!')).toBeInTheDocument();
    expect(screen.getByText(/criado com sucesso/)).toBeInTheDocument();
  });

  it('cadastra novo endereço e o seleciona', async () => {
    mockCart(cart());
    vi.mocked(addressesApi.getAddresses).mockResolvedValue([]);
    vi.mocked(addressesApi.createAddress).mockResolvedValue(address({ id: 99, street: 'Rua Nova' }));
    renderAt('/checkout');

    expect(screen.getByRole('button', { name: 'Continuar' })).toBeDisabled();
    await userEvent.click(await screen.findByRole('button', { name: /Adicionar novo endereço/ }));
    const f = within(screen.getByRole('form', { name: 'Novo endereço' }));
    await userEvent.type(f.getByLabelText('CEP'), '01000000');
    await userEvent.type(f.getByLabelText('Rua'), 'Rua Nova');
    await userEvent.type(f.getByLabelText('Número'), '5');
    await userEvent.type(f.getByLabelText('Bairro'), 'Centro');
    await userEvent.type(f.getByLabelText('Cidade'), 'São Paulo');
    await userEvent.type(f.getByLabelText('UF'), 'sp');
    await userEvent.click(f.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText(/Rua Nova/)).toBeInTheDocument();
    expect(addressesApi.createAddress).toHaveBeenCalledWith({
      zipCode: '01000-000', street: 'Rua Nova', number: '5', neighborhood: 'Centro', city: 'São Paulo', state: 'SP',
    });
    expect(screen.getByRole('radio')).toBeChecked();
    expect(screen.getByRole('button', { name: 'Continuar' })).toBeEnabled();
  });

  it('erros ao salvar endereço e ao finalizar são exibidos', async () => {
    mockCart(cart());
    vi.mocked(addressesApi.getAddresses).mockResolvedValue([address()]);
    vi.mocked(addressesApi.createAddress).mockRejectedValue({ response: { status: 500, data: {} } });
    vi.mocked(ordersApi.checkout).mockRejectedValue({ response: { status: 400, data: {} } });
    renderAt('/checkout');

    await userEvent.click(await screen.findByRole('button', { name: /Adicionar novo endereço/ }));
    const form = screen.getByRole('form', { name: 'Novo endereço' });
    // formulário inválido não chega à API
    await userEvent.click(within(form).getByRole('button', { name: 'Salvar' }));
    expect(within(form).getByText('Informe o CEP.')).toBeInTheDocument();
    expect(addressesApi.createAddress).not.toHaveBeenCalled();

    const f = within(form);
    await userEvent.type(f.getByLabelText('CEP'), '01000000');
    await userEvent.type(f.getByLabelText('Rua'), 'Rua Nova');
    await userEvent.type(f.getByLabelText('Número'), '5');
    await userEvent.type(f.getByLabelText('Bairro'), 'Centro');
    await userEvent.type(f.getByLabelText('Cidade'), 'Santos');
    await userEvent.type(f.getByLabelText('UF'), 'SP');
    await userEvent.click(f.getByRole('button', { name: 'Salvar' }));
    expect(await screen.findByText('Erro ao salvar endereço.')).toBeInTheDocument();
    await userEvent.click(within(form).getByRole('button', { name: 'Cancelar' }));

    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar e pagar com Pix' }));
    expect(await screen.findByText(/Erro ao finalizar pedido/)).toBeInTheDocument();
  });

  it('carrinho vazio redireciona para o carrinho', async () => {
    mockCart({ items: [], total: 0, itemCount: 0 });
    vi.mocked(addressesApi.getAddresses).mockResolvedValue([]);
    renderAt('/checkout');
    expect(await screen.findByText('Seu carrinho está vazio')).toBeInTheDocument();
  });
});
