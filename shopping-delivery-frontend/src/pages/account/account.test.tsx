import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../../contexts/AuthContext';
import AccountLayout from './AccountLayout';
import OrdersPage from './OrdersPage';
import OrderDetailPage from './OrderDetailPage';
import PurchaseHistoryPage from './PurchaseHistoryPage';
import AddressesPage from './AddressesPage';
import * as ordersApi from '../../api/orders';
import * as addressesApi from '../../api/addresses';
import { PIX_CODE, address, order, payment } from '../../test/fixtures';
import { loginAs } from '../../test/utils';
import type { OrderItem } from '../../types';

vi.mock('../../api/orders');
vi.mock('../../api/addresses');

const renderAt = (path: string) => render(
  <MemoryRouter initialEntries={[path]}>
    <AuthProvider>
      <Routes>
        <Route element={<AccountLayout />}>
          <Route path="/orders" element={<OrdersPage />} />
          <Route path="/orders/:id" element={<OrderDetailPage />} />
          <Route path="/purchases" element={<PurchaseHistoryPage />} />
          <Route path="/addresses" element={<AddressesPage />} />
        </Route>
        <Route path="/products/:id" element={<div>PRODUCT PAGE</div>} />
        <Route path="/" element={<div>HOME PAGE</div>} />
      </Routes>
    </AuthProvider>
  </MemoryRouter>,
);

const item = (over: Partial<OrderItem> = {}): OrderItem => ({
  id: 1, productId: 1, productName: 'Caneca', quantity: 1, unitPrice: 10, subtotal: 10, ...over,
});

const httpError = (message: string) => ({ response: { status: 400, data: { message } } });

beforeEach(() => {
  vi.resetAllMocks();
  loginAs('CUSTOMER', { name: 'Ana Souza', email: 'ana@a.com' });
});

describe('AccountLayout', () => {
  it('mostra o usuário e as abas, com a aba atual marcada', async () => {
    vi.mocked(ordersApi.getOrders).mockResolvedValue([]);
    renderAt('/orders');

    expect(screen.getByRole('heading', { name: 'Minha conta' })).toBeInTheDocument();
    expect(screen.getByText('Ana Souza · ana@a.com')).toBeInTheDocument();
    const nav = within(screen.getByRole('navigation', { name: 'Minha conta' }));
    expect(nav.getByRole('link', { name: 'Meus pedidos' })).toHaveAttribute('aria-current', 'page');
    expect(nav.getByRole('link', { name: 'Histórico de compras' })).toHaveAttribute('href', '/purchases');
    expect(nav.getByRole('link', { name: 'Endereços' })).toHaveAttribute('href', '/addresses');
    expect(await screen.findByText('Nenhum pedido ainda')).toBeInTheDocument();
  });
});

describe('OrdersPage', () => {
  it('lista pedidos com status traduzido, prévia de itens e resumo', async () => {
    vi.mocked(ordersApi.getOrders).mockResolvedValue([
      order(),
      order({ id: 4, status: 'DELIVERED', total: 100, items: [] }),
      order({ id: 5, status: 'CANCELLED', total: 1000 }),
    ]);
    renderAt('/orders');

    expect(await screen.findByText('Pedido #3')).toBeInTheDocument();
    expect(screen.getAllByText('Entregue').length).toBeGreaterThan(0);
    expect(screen.getAllByText(/1 item · Caneca/)).toHaveLength(2);
    expect(screen.getByText('0 itens')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Pedido #3/ })).toHaveAttribute('href', '/orders/3');

    // resumo: cancelado não entra no total gasto (59,80 + 100,00)
    const resumo = (label: string) => screen.getByText(label, { selector: 'dt' }).nextElementSibling;
    expect(resumo('Pedidos')).toHaveTextContent('3');
    expect(resumo('Total gasto')).toHaveTextContent(/159,80/);
    expect(resumo('Em andamento')).toHaveTextContent('1');
    expect(resumo('Entregues')).toHaveTextContent('1');
  });

  it('filtra por status e mostra a contagem de cada filtro', async () => {
    vi.mocked(ordersApi.getOrders).mockResolvedValue([
      order({ id: 1, status: 'PENDING' }), order({ id: 2, status: 'SHIPPED' }), order({ id: 3, status: 'CANCELLED' }),
    ]);
    renderAt('/orders');
    await screen.findByText('Pedido #1');

    await userEvent.click(screen.getByRole('button', { name: 'Em andamento (2)' }));
    expect(screen.getByRole('button', { name: 'Em andamento (2)' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByText('Pedido #3')).not.toBeInTheDocument();
    expect(screen.getByText('Pedido #2')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Cancelados (1)' }));
    expect(screen.getByText('Pedido #3')).toBeInTheDocument();
    expect(screen.queryByText('Pedido #1')).not.toBeInTheDocument();
  });

  it('busca por produto ou número; sem resultado oferece limpar filtros', async () => {
    vi.mocked(ordersApi.getOrders).mockResolvedValue([
      order({ id: 1, items: [item({ productName: 'Câmera' })] }), order({ id: 2 }),
    ]);
    renderAt('/orders');
    const search = await screen.findByRole('searchbox', { name: 'Buscar pedidos' });

    await userEvent.type(search, 'camera');
    expect(screen.getByText('Pedido #1')).toBeInTheDocument();
    expect(screen.queryByText('Pedido #2')).not.toBeInTheDocument();

    await userEvent.clear(search);
    await userEvent.type(search, '#2');
    expect(screen.getByText('Pedido #2')).toBeInTheDocument();
    expect(screen.queryByText('Pedido #1')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Entregues (0)' }));
    expect(screen.getByText('Nenhum pedido encontrado com esses filtros.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Limpar filtros' }));
    expect(search).toHaveValue('');
    expect(screen.getByText('Pedido #1')).toBeInTheDocument();
    expect(screen.getByText('Pedido #2')).toBeInTheDocument();
  });

  it('pagina a lista a cada 5 pedidos e volta à primeira página ao filtrar', async () => {
    vi.mocked(ordersApi.getOrders).mockResolvedValue(
      Array.from({ length: 7 }, (_, i) => order({ id: i + 1, status: i === 6 ? 'DELIVERED' : 'PENDING' })),
    );
    renderAt('/orders');
    await screen.findByText('Pedido #1');
    expect(screen.queryByText('Pedido #6')).not.toBeInTheDocument();
    expect(screen.getByText('Página 1 de 2')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Próxima página' }));
    expect(screen.getByText('Pedido #6')).toBeInTheDocument();
    expect(screen.queryByText('Pedido #1')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Todos (7)' }));
    expect(screen.getByText('Pedido #1')).toBeInTheDocument();
  });

  it('erro ao carregar permite tentar novamente', async () => {
    vi.mocked(ordersApi.getOrders)
      .mockRejectedValueOnce(httpError('Falha X'))
      .mockResolvedValueOnce([order()]);
    renderAt('/orders');

    expect(await screen.findByText('Falha X')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }));
    expect(await screen.findByText('Pedido #3')).toBeInTheDocument();
    expect(ordersApi.getOrders).toHaveBeenCalledTimes(2);
  });

  it('pedido aguardando Pix tem selo e não entra no total gasto', async () => {
    vi.mocked(ordersApi.getOrders).mockResolvedValue([
      order({ id: 1, payment: payment() }),
      order({ id: 2, status: 'CONFIRMED', total: 10, payment: payment({ status: 'PAID' }) }),
    ]);
    renderAt('/orders');
    expect(await screen.findByText('Pedido #1')).toBeInTheDocument();
    expect(screen.getAllByText('Aguardando Pix')).toHaveLength(1);
    expect(screen.getByText('Total gasto', { selector: 'dt' }).nextElementSibling).toHaveTextContent(/10,00/);
  });

  it('estado vazio', async () => {
    vi.mocked(ordersApi.getOrders).mockResolvedValue([]);
    renderAt('/orders');
    expect(await screen.findByText('Nenhum pedido ainda')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Começar a Comprar' })).toHaveAttribute('href', '/');
  });
});

describe('OrderDetailPage', () => {
  it('mostra itens, endereço, status e a linha do tempo', async () => {
    vi.mocked(ordersApi.getOrder).mockResolvedValue(order({ status: 'SHIPPED' }));
    renderAt('/orders/3');

    expect(await screen.findByText('Pedido #3')).toBeInTheDocument();
    expect(screen.getByText(/Rua A, 10/)).toBeInTheDocument();
    expect(screen.getByText('2 unidades')).toBeInTheDocument();
    expect(ordersApi.getOrder).toHaveBeenCalledWith(3);

    const timeline = within(screen.getByRole('list', { name: 'Andamento do pedido' }));
    const steps = timeline.getAllByRole('listitem');
    expect(steps.map(s => s.textContent)).toEqual(['Pendente', 'Confirmado', 'Enviado', '4Entregue']);
    expect(steps[2]).toHaveAttribute('aria-current', 'step');
  });

  it('item linka para a página do produto', async () => {
    vi.mocked(ordersApi.getOrder).mockResolvedValue(order({ status: 'DELIVERED', items: [item({ productId: 9, quantity: 1 })] }));
    renderAt('/orders/3');
    expect(await screen.findByText('1 unidade')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('link', { name: 'Caneca' }));
    expect(screen.getByText('PRODUCT PAGE')).toBeInTheDocument();
  });

  it('pedido cancelado mostra aviso em vez da linha do tempo', async () => {
    vi.mocked(ordersApi.getOrder).mockResolvedValue(order({ status: 'CANCELLED', address: address({ number: '', complement: '' }) }));
    renderAt('/orders/3');
    expect(await screen.findByText(/Este pedido foi cancelado/)).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Andamento do pedido' })).not.toBeInTheDocument();
    expect(screen.getByText('Rua A')).toBeInTheDocument();
  });

  it('Pix pendente: mostra QR/copia e cola e recarrega o pedido quando é pago', async () => {
    vi.mocked(ordersApi.getOrder)
      .mockResolvedValueOnce(order({ payment: payment() }))
      .mockResolvedValue(order({ status: 'CONFIRMED', payment: payment({ status: 'PAID', pixCopyPaste: null }) }));
    vi.mocked(ordersApi.getPayment).mockResolvedValue(payment({ status: 'PAID', pixCopyPaste: null, expiresInSeconds: 0 }));
    renderAt('/orders/3');

    expect(await screen.findByLabelText('Código Pix copia e cola')).toHaveValue(PIX_CODE);
    // após o polling (5 s) o pedido é recarregado e a seção vira só o selo "Pix pago"
    expect(await screen.findByText('Pix pago', {}, { timeout: 7000 })).toBeInTheDocument();
    expect(ordersApi.getOrder).toHaveBeenCalledTimes(2);
    expect(within(screen.getByRole('list', { name: 'Andamento do pedido' })).getAllByRole('listitem')[1])
      .toHaveAttribute('aria-current', 'step');
  }, 10_000);

  it.each([
    ['PAID', 'Pix pago'], ['EXPIRED', 'Pix expirado'], ['REFUND_PENDING', 'Estorno pendente'],
  ] as const)('pagamento %s aparece como selo', async (status, label) => {
    vi.mocked(ordersApi.getOrder).mockResolvedValue(order({ status: 'CONFIRMED', payment: payment({ status, pixCopyPaste: null }) }));
    renderAt('/orders/3');
    expect(await screen.findByText(label)).toBeInTheDocument();
    expect(screen.queryByLabelText('Código Pix copia e cola')).not.toBeInTheDocument();
  });

  it('cliente cancela pedido ainda não enviado (com confirmação)', async () => {
    vi.mocked(ordersApi.getOrder).mockResolvedValue(order({ status: 'CONFIRMED', canCancel: true, payment: payment({ status: 'PAID', pixCopyPaste: null }) }));
    vi.mocked(ordersApi.cancelOrder).mockResolvedValue(order({ status: 'CANCELLED', canCancel: false, payment: payment({ status: 'REFUND_PENDING', pixCopyPaste: null }) }));
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    renderAt('/orders/3');

    await userEvent.click(await screen.findByRole('button', { name: 'Cancelar pedido' }));
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('devolvido por Pix'));
    expect(ordersApi.cancelOrder).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar pedido' }));
    expect(await screen.findByText('Estorno pendente')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancelar pedido' })).not.toBeInTheDocument();
    confirm.mockRestore();
  });

  it('erro ao cancelar aparece traduzido; pedido sem Pix não menciona devolução', async () => {
    vi.mocked(ordersApi.getOrder).mockResolvedValue(order({ canCancel: true }));
    vi.mocked(ordersApi.cancelOrder).mockRejectedValue(httpError('Order can no longer be cancelled'));
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderAt('/orders/3');
    await userEvent.click(await screen.findByRole('button', { name: 'Cancelar pedido' }));
    expect(confirm).toHaveBeenCalledWith('Cancelar o pedido #3?');
    expect(await screen.findByRole('alert')).toHaveTextContent('já foi enviado');
    confirm.mockRestore();
  });

  it('arrependimento: mostra o prazo, envia o motivo e confirma a solicitação', async () => {
    vi.mocked(ordersApi.getOrder).mockResolvedValue(order({
      status: 'DELIVERED', canRequestReturn: true, deliveredAt: '2026-03-15T12:00:00', returnDeadline: '2026-03-22T12:00:00',
    }));
    vi.mocked(ordersApi.requestReturn).mockResolvedValue(order({
      status: 'DELIVERED', canRequestReturn: false, returnRequestedAt: '2026-03-16T09:00:00',
    }));
    renderAt('/orders/3');

    expect(await screen.findByText(/até/)).toHaveTextContent(/22.*mar.*2026/);
    await userEvent.click(screen.getByRole('button', { name: 'Solicitar devolução' }));
    await userEvent.click(screen.getByRole('button', { name: 'Voltar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Solicitar devolução' }));
    await userEvent.type(screen.getByLabelText('Motivo (opcional)'), 'Não serviu');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar devolução' }));

    expect(ordersApi.requestReturn).toHaveBeenCalledWith(3, 'Não serviu');
    expect(await screen.findByRole('status')).toHaveTextContent(/Devolução solicitada em 16/);
    expect(screen.queryByRole('button', { name: 'Solicitar devolução' })).not.toBeInTheDocument();
  });

  it('pedido inexistente volta para a lista', async () => {
    vi.mocked(ordersApi.getOrder).mockRejectedValue(new Error('404'));
    vi.mocked(ordersApi.getOrders).mockResolvedValue([]);
    renderAt('/orders/99');
    expect(await screen.findByText('Nenhum pedido ainda')).toBeInTheDocument();
  });
});

describe('PurchaseHistoryPage', () => {
  const history = [
    order({ id: 1, status: 'DELIVERED', createdAt: '2026-01-10T10:00:00',
      items: [item({ productId: 1, productName: 'Caneca', quantity: 3, subtotal: 30 })] }),
    order({ id: 2, status: 'PENDING', createdAt: '2026-02-10T10:00:00',
      items: [item({ id: 2, productId: 2, productName: 'Camiseta', quantity: 1, unitPrice: 50, subtotal: 50 })] }),
    order({ id: 3, status: 'CANCELLED', createdAt: '2026-03-10T10:00:00',
      items: [item({ id: 3, productId: 3, productName: 'TV', subtotal: 999 })] }),
  ];

  const productNames = () => screen.getAllByRole('row').slice(1).map(r => within(r).getAllByRole('link')[0].textContent);

  it('consolida produtos comprados, sem pedidos cancelados', async () => {
    vi.mocked(ordersApi.getOrders).mockResolvedValue(history);
    renderAt('/purchases');

    expect(await screen.findByRole('table')).toBeInTheDocument();
    expect(productNames()).toEqual(['Camiseta', 'Caneca']); // mais recente primeiro
    expect(screen.queryByText('TV')).not.toBeInTheDocument();
    expect(screen.getByText(/em compras/)).toHaveTextContent(/2 produtos · 4 unidades · R\$\s?80,00 em compras/);

    const caneca = screen.getByRole('row', { name: /Caneca/ });
    expect(within(caneca).getByRole('link', { name: 'Caneca' })).toHaveAttribute('href', '/products/1');
    expect(within(caneca).getByRole('link', { name: '#1' })).toHaveAttribute('href', '/orders/1');
    expect(within(caneca).getByText(/1 pedido · último preço/)).toBeInTheDocument();
  });

  it('ordena por quantidade e por gasto', async () => {
    vi.mocked(ordersApi.getOrders).mockResolvedValue(history);
    renderAt('/purchases');
    const select = await screen.findByRole('combobox', { name: 'Ordenar por' });

    await userEvent.selectOptions(select, 'quantity');
    expect(productNames()).toEqual(['Caneca', 'Camiseta']);
    await userEvent.selectOptions(select, 'spent');
    expect(productNames()).toEqual(['Camiseta', 'Caneca']);
  });

  it('textos no singular para um produto/unidade', async () => {
    vi.mocked(ordersApi.getOrders).mockResolvedValue([order({ items: [item()] })]);
    renderAt('/purchases');
    expect(await screen.findByText(/em compras/)).toHaveTextContent(/1 produto · 1 unidade ·/);
  });

  it('estado vazio quando só há pedidos cancelados', async () => {
    vi.mocked(ordersApi.getOrders).mockResolvedValue([history[2]]);
    renderAt('/purchases');
    expect(await screen.findByText('Você ainda não comprou nenhum produto')).toBeInTheDocument();
  });

  it('erro ao carregar', async () => {
    vi.mocked(ordersApi.getOrders).mockRejectedValue(new Error('Network Error'));
    renderAt('/purchases');
    expect(await screen.findByText(/conectar ao servidor/)).toBeInTheDocument();
  });
});

describe('AddressesPage', () => {
  it('lista endereços e cadastra um novo com validação', async () => {
    vi.mocked(addressesApi.getAddresses).mockResolvedValue([address()]);
    vi.mocked(addressesApi.createAddress).mockResolvedValue(address({ id: 8, street: 'Rua Nova', complement: undefined }));
    renderAt('/addresses');

    expect(await screen.findByText('Rua A, 10 — Apto 2')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Adicionar novo endereço/ }));
    const form = within(screen.getByRole('form', { name: 'Novo endereço' }));
    await userEvent.click(form.getByRole('button', { name: 'Salvar' }));
    expect(form.getByText('Informe o CEP.')).toBeInTheDocument();
    expect(addressesApi.createAddress).not.toHaveBeenCalled();

    await userEvent.type(form.getByLabelText('CEP'), '01000000');
    await userEvent.type(form.getByLabelText('Rua'), 'Rua Nova');
    await userEvent.type(form.getByLabelText('Número'), '5');
    await userEvent.type(form.getByLabelText('Bairro'), 'Centro');
    await userEvent.type(form.getByLabelText('Cidade'), 'Santos');
    await userEvent.type(form.getByLabelText('UF'), 'SP');
    await userEvent.click(form.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Rua Nova, 10')).toBeInTheDocument();
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
  });

  it('exclusão pede confirmação; "Manter" cancela', async () => {
    vi.mocked(addressesApi.getAddresses).mockResolvedValue([address()]);
    vi.mocked(addressesApi.deleteAddress).mockResolvedValue({} as never);
    renderAt('/addresses');

    await userEvent.click(await screen.findByRole('button', { name: 'Excluir endereço Rua A, 10' }));
    await userEvent.click(screen.getByRole('button', { name: 'Manter' }));
    expect(addressesApi.deleteAddress).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Excluir endereço Rua A, 10' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar exclusão' }));
    expect(addressesApi.deleteAddress).toHaveBeenCalledWith(7);
    expect(await screen.findByText('Nenhum endereço cadastrado.')).toBeInTheDocument();
  });

  it('endereço usado em pedido não é excluído e mostra o motivo', async () => {
    vi.mocked(addressesApi.getAddresses).mockResolvedValue([address({ number: '', complement: '' })]);
    vi.mocked(addressesApi.deleteAddress).mockRejectedValue(httpError('Address is linked to an order'));
    renderAt('/addresses');

    await userEvent.click(await screen.findByRole('button', { name: /Excluir endereço Rua A/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar exclusão' }));
    expect(await screen.findByText('Este endereço já foi usado em um pedido e não pode ser excluído.')).toBeInTheDocument();
    expect(screen.getByText('Rua A')).toBeInTheDocument();
  });

  it('erro ao carregar e formulário cancelável', async () => {
    vi.mocked(addressesApi.getAddresses).mockRejectedValue(new Error('Network Error'));
    renderAt('/addresses');
    expect(await screen.findByText(/conectar ao servidor/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Adicionar novo endereço/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
  });
});
