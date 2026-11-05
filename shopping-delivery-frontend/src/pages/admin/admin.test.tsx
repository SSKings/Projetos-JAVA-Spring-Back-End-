import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdminDashboardPage from './AdminDashboardPage';
import AdminProductsPage from './AdminProductsPage';
import AdminOrdersPage from './AdminOrdersPage';
import AdminUsersPage from './AdminUsersPage';
import AdminAuditPage from './AdminAuditPage';
import * as adminApi from '../../api/admin';
import { loginAs, page, renderWithProviders } from '../../test/utils';
import { adminOrder, adminProduct, adminUser, order, payment } from '../../test/fixtures';

vi.mock('../../api/admin');

const httpError = (message: string) => ({ response: { status: 400, data: { message } } });

beforeEach(() => {
  vi.resetAllMocks();
  loginAs('ADMIN', { name: 'Chefe', email: 'admin@x.com' });
});

describe('AdminDashboardPage', () => {
  it('mostra os indicadores e o alerta de estoque baixo', async () => {
    vi.mocked(adminApi.getDashboard).mockResolvedValue({
      totalProducts: 12, activeProducts: 10, lowStockProducts: 2,
      totalCustomers: 30, totalOrders: 40, pendingOrders: 3, revenue: 1500,
    });
    renderWithProviders(<AdminDashboardPage />);

    expect(await screen.findByText('30')).toBeInTheDocument();
    expect(screen.getByText('3 pendente(s)')).toBeInTheDocument();
    expect(screen.getByText(/R\$\s?1\.500,00/)).toBeInTheDocument();
    expect(screen.getByText(/2 produto\(s\) ativo\(s\)/)).toBeInTheDocument();
  });

  it('mostra erro da API', async () => {
    vi.mocked(adminApi.getDashboard).mockRejectedValue(new Error('Network Error'));
    renderWithProviders(<AdminDashboardPage />);
    expect(await screen.findByText(/conectar ao servidor/)).toBeInTheDocument();
  });
});

describe('AdminProductsPage', () => {
  it('lista produtos (inclusive inativos) e busca', async () => {
    vi.mocked(adminApi.getAdminProducts).mockResolvedValue(page([
      adminProduct(), adminProduct({ id: 2, name: 'Velha', active: false, stockQuantity: 50, imageUrl: '' }),
    ]));
    renderWithProviders(<AdminProductsPage />);

    expect(await screen.findByText('Velha')).toBeInTheDocument();
    expect(screen.getByText('Inativo')).toBeInTheDocument();

    await userEvent.type(screen.getByPlaceholderText('Buscar produto...'), 'cane{enter}');
    await waitFor(() => expect(adminApi.getAdminProducts).toHaveBeenLastCalledWith(0, 'cane'));
  });

  it('desativa e reativa produtos', async () => {
    vi.mocked(adminApi.getAdminProducts).mockResolvedValue(page([
      adminProduct(), adminProduct({ id: 2, name: 'Velha', active: false }),
    ]));
    vi.mocked(adminApi.deactivateProduct).mockResolvedValue({} as never);
    vi.mocked(adminApi.updateProduct).mockResolvedValue(adminProduct());
    renderWithProviders(<AdminProductsPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Desativar' }));
    expect(adminApi.deactivateProduct).toHaveBeenCalledWith(1);

    await userEvent.click(screen.getByRole('button', { name: 'Reativar' }));
    expect(adminApi.updateProduct).toHaveBeenCalledWith(2, expect.objectContaining({ active: true, name: 'Velha' }));
    await waitFor(() => expect(adminApi.getAdminProducts).toHaveBeenCalledTimes(3));
  });

  it('cria produto pelo modal', async () => {
    vi.mocked(adminApi.getAdminProducts).mockResolvedValue(page([]));
    vi.mocked(adminApi.createProduct).mockResolvedValue(adminProduct());
    renderWithProviders(<AdminProductsPage />);

    expect(await screen.findByText('Nenhum produto encontrado')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Novo produto/ }));
    await userEvent.type(screen.getByLabelText('Nome'), 'Caneca');
    await userEvent.type(screen.getByLabelText('Preço (R$)'), '29.90');
    await userEvent.clear(screen.getByLabelText('Estoque'));
    await userEvent.type(screen.getByLabelText('Estoque'), '8');
    await userEvent.click(screen.getByLabelText('Visível na loja'));
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(adminApi.createProduct).toHaveBeenCalledWith({
      name: 'Caneca', description: '', imageUrl: '', price: 29.9, stockQuantity: 8, active: false,
    });
    await waitFor(() => expect(screen.queryByText('Novo produto', { selector: 'h2' })).not.toBeInTheDocument());
  });

  it('edita produto e mostra erro de validação do back-end', async () => {
    vi.mocked(adminApi.getAdminProducts).mockResolvedValue(page([adminProduct()]));
    vi.mocked(adminApi.updateProduct).mockRejectedValue(httpError('Price must be greater than zero'));
    renderWithProviders(<AdminProductsPage />);

    await userEvent.click(await screen.findByLabelText('Editar'));
    expect(screen.getByLabelText('Nome')).toHaveValue('Caneca');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Price must be greater than zero')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(screen.queryByLabelText('Nome')).not.toBeInTheDocument();
  });

  it('erro ao desativar aparece na página', async () => {
    vi.mocked(adminApi.getAdminProducts).mockResolvedValue(page([adminProduct()]));
    vi.mocked(adminApi.deactivateProduct).mockRejectedValue(httpError('Product not found: 1'));
    renderWithProviders(<AdminProductsPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Desativar' }));
    expect(await screen.findByText('Product not found: 1')).toBeInTheDocument();
  });
});

describe('AdminOrdersPage', () => {
  const optionsOf = (select: HTMLElement) =>
    within(select).getAllByRole('option').map(o => ({ label: o.textContent, disabled: (o as HTMLOptionElement).disabled }));

  it('mostra todos os status, com o atual marcado e só as transições do back-end habilitadas', async () => {
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([
      adminOrder(), adminOrder({ id: 4, status: 'SHIPPED' }), adminOrder({ id: 5, status: 'DELIVERED' }),
    ]));
    renderWithProviders(<AdminOrdersPage />);

    const pending = await screen.findByRole('combobox', { name: 'Status do pedido #3' });
    expect(pending).toHaveValue('PENDING');
    expect(optionsOf(pending)).toEqual([
      { label: 'Pendente (atual)', disabled: false },
      { label: 'Confirmado', disabled: false },
      { label: 'Enviado', disabled: false },
      { label: 'Entregue', disabled: false },
      { label: 'Cancelado', disabled: false },
    ]);

    const shipped = screen.getByRole('combobox', { name: 'Status do pedido #4' });
    expect(optionsOf(shipped).filter(o => !o.disabled).map(o => o.label)).toEqual(['Enviado (atual)', 'Entregue']);

    const delivered = screen.getByRole('combobox', { name: 'Status do pedido #5' });
    expect(delivered).toBeDisabled();
    expect(screen.getByText('Finalizado')).toBeInTheDocument();
  });

  it('respeita as transições que o back-end enviar (fonte única da regra)', async () => {
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([adminOrder({ nextStatuses: ['CANCELLED'] })]));
    renderWithProviders(<AdminOrdersPage />);
    const select = await screen.findByRole('combobox', { name: 'Status do pedido #3' });
    expect(optionsOf(select).filter(o => !o.disabled).map(o => o.label)).toEqual(['Pendente (atual)', 'Cancelado']);
  });

  it('filtra por status', async () => {
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([adminOrder()]));
    renderWithProviders(<AdminOrdersPage />);
    await screen.findByText('#3');

    await userEvent.click(screen.getByRole('button', { name: 'Pendente' }));
    await waitFor(() => expect(adminApi.getAdminOrders).toHaveBeenLastCalledWith(0, 'PENDING'));
    await userEvent.click(screen.getByRole('button', { name: 'Todos' }));
    await waitFor(() => expect(adminApi.getAdminOrders).toHaveBeenLastCalledWith(0, undefined));
  });

  it.each([
    ['CONFIRMED', 'Confirmado'],
    ['SHIPPED', 'Enviado'],
    ['DELIVERED', 'Entregue'],
  ] as const)('altera PENDING → %s (pulando etapas), mostra confirmação e recarrega', async (next, label) => {
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([adminOrder()]));
    vi.mocked(adminApi.updateOrderStatus).mockResolvedValue(adminOrder({ status: next }));
    const confirm = vi.spyOn(window, 'confirm');
    renderWithProviders(<AdminOrdersPage />);

    await userEvent.selectOptions(await screen.findByRole('combobox', { name: 'Status do pedido #3' }), next);

    expect(adminApi.updateOrderStatus).toHaveBeenCalledWith(3, next);
    expect(confirm).not.toHaveBeenCalled();
    expect(await screen.findByRole('status')).toHaveTextContent(`Pedido #3 atualizado para ${label}.`);
    await waitFor(() => expect(adminApi.getAdminOrders).toHaveBeenCalledTimes(2));
    confirm.mockRestore();
  });

  it('cancelar pede confirmação; recusar não chama a API', async () => {
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([adminOrder()]));
    vi.mocked(adminApi.updateOrderStatus).mockResolvedValue(adminOrder({ status: 'CANCELLED' }));
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    renderWithProviders(<AdminOrdersPage />);
    const select = await screen.findByRole('combobox', { name: 'Status do pedido #3' });

    await userEvent.selectOptions(select, 'CANCELLED');
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('Os itens voltam ao estoque'));
    expect(adminApi.updateOrderStatus).not.toHaveBeenCalled();
    expect(select).toHaveValue('PENDING');

    await userEvent.selectOptions(select, 'CANCELLED');
    expect(adminApi.updateOrderStatus).toHaveBeenCalledWith(3, 'CANCELLED');
    expect(await screen.findByRole('status')).toHaveTextContent('Pedido #3 atualizado para Cancelado.');
    confirm.mockRestore();
  });

  it('desabilita o seletor enquanto salva (evita duplo envio)', async () => {
    let resolve!: (o: ReturnType<typeof adminOrder>) => void;
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([adminOrder()]));
    vi.mocked(adminApi.updateOrderStatus).mockReturnValue(new Promise(r => { resolve = r; }));
    renderWithProviders(<AdminOrdersPage />);
    const select = await screen.findByRole('combobox', { name: 'Status do pedido #3' });

    await userEvent.selectOptions(select, 'CONFIRMED');
    expect(select).toBeDisabled();
    expect(screen.getByText('Salvando…')).toBeInTheDocument();

    resolve(adminOrder({ status: 'CONFIRMED' }));
    await waitFor(() => expect(select).toBeEnabled());
  });

  it('transição recusada pelo back-end aparece traduzida', async () => {
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([adminOrder()]));
    vi.mocked(adminApi.updateOrderStatus).mockRejectedValue(httpError('Cannot change order status from PENDING to SHIPPED'));
    renderWithProviders(<AdminOrdersPage />);

    await userEvent.selectOptions(await screen.findByRole('combobox', { name: 'Status do pedido #3' }), 'SHIPPED');
    expect(await screen.findByRole('alert')).toHaveTextContent('Não é possível mudar o pedido de Pendente para Enviado.');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('falha de rede (ex.: CORS bloqueando o PATCH) mostra a mensagem de conexão', async () => {
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([adminOrder()]));
    vi.mocked(adminApi.updateOrderStatus).mockRejectedValue(new Error('Network Error'));
    renderWithProviders(<AdminOrdersPage />);

    await userEvent.selectOptions(await screen.findByRole('combobox', { name: 'Status do pedido #3' }), 'CONFIRMED');
    expect(await screen.findByRole('alert')).toHaveTextContent(/conectar ao servidor/);
  });

  it('pedido com Pix pendente: selo, só cancelar habilitado e confirmação manual do Pix', async () => {
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([
      adminOrder({ nextStatuses: ['CANCELLED'], payment: payment({ txid: 'TXABC' }) }),
    ]));
    vi.mocked(adminApi.confirmPayment).mockResolvedValue(adminOrder({ status: 'CONFIRMED', payment: payment({ status: 'PAID' }) }));
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    renderWithProviders(<AdminOrdersPage />);

    expect(await screen.findByText('Aguardando Pix')).toBeInTheDocument();
    const select = screen.getByRole('combobox', { name: 'Status do pedido #3' });
    expect(optionsOf(select).filter(o => !o.disabled).map(o => o.label)).toEqual(['Pendente (atual)', 'Cancelado']);

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar Pix' }));
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('TXABC'));
    expect(adminApi.confirmPayment).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar Pix' }));
    expect(adminApi.confirmPayment).toHaveBeenCalledWith(3);
    expect(await screen.findByRole('status')).toHaveTextContent('Pagamento do pedido #3 confirmado');
    await waitFor(() => expect(adminApi.getAdminOrders).toHaveBeenCalledTimes(2));
    confirm.mockRestore();
  });

  it('erro ao confirmar Pix aparece traduzido', async () => {
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([adminOrder({ nextStatuses: ['CANCELLED'], payment: payment() })]));
    vi.mocked(adminApi.confirmPayment).mockRejectedValue(httpError('Payment is not pending'));
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderWithProviders(<AdminOrdersPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar Pix' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Este pagamento não está mais pendente.');
    confirm.mockRestore();
  });

  it('detalhe mostra pagamento; estorno pendente tem aviso', async () => {
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([
      adminOrder({ status: 'CANCELLED', payment: payment({ status: 'REFUND_PENDING', txid: 'TXR', paidAt: '2026-03-15T12:05:00' }) }),
    ]));
    renderWithProviders(<AdminOrdersPage />);

    await userEvent.click(await screen.findByText('#3'));
    expect(screen.getAllByText(/Estorno pendente/).length).toBeGreaterThan(0);
    expect(screen.getByText(/identificador TXR/)).toBeInTheDocument();
    expect(screen.getByText(/Pago em/)).toBeInTheDocument();
    expect(screen.getByText(/devolva o valor/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirmar Pix' })).not.toBeInTheDocument();
  });

  it('devolução solicitada: selo, motivo e "Registrar estorno" com confirmação', async () => {
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([
      adminOrder({ status: 'DELIVERED', returnRequestedAt: '2026-03-16T09:00:00', returnReason: 'Não serviu', payment: payment({ status: 'PAID' }) }),
      adminOrder({ id: 4, status: 'CANCELLED', payment: payment({ status: 'REFUND_PENDING' }) }),
      adminOrder({ id: 5, status: 'DELIVERED', payment: payment({ status: 'PAID' }) }),
    ]));
    vi.mocked(adminApi.refundPayment).mockResolvedValue(adminOrder());
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValue(true);
    renderWithProviders(<AdminOrdersPage />);

    expect(await screen.findByText('Devolução solicitada')).toBeInTheDocument();
    const refunds = screen.getAllByRole('button', { name: 'Registrar estorno' });
    expect(refunds).toHaveLength(2);   // pedido 5 (pago, sem devolução) não tem estorno

    await userEvent.click(refunds[0]);
    expect(adminApi.refundPayment).not.toHaveBeenCalled();
    await userEvent.click(refunds[0]);
    expect(adminApi.refundPayment).toHaveBeenCalledWith(3);
    expect(await screen.findByRole('status')).toHaveTextContent('Estorno do pedido #3 registrado.');

    await userEvent.click(screen.getByText('#3'));
    expect(screen.getByText('Motivo da devolução: Não serviu')).toBeInTheDocument();
    confirm.mockRestore();
  });

  it('erro ao registrar estorno', async () => {
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([adminOrder({ status: 'CANCELLED', payment: payment({ status: 'REFUND_PENDING' }) })]));
    vi.mocked(adminApi.refundPayment).mockRejectedValue(httpError('Payment cannot be refunded'));
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderWithProviders(<AdminOrdersPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Registrar estorno' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('não pode ser estornado');
    confirm.mockRestore();
  });

  it('erro ao carregar a lista', async () => {
    vi.mocked(adminApi.getAdminOrders).mockRejectedValue(httpError('Falha'));
    renderWithProviders(<AdminOrdersPage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Falha');
  });

  it('expande o pedido para ver itens e endereço', async () => {
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([adminOrder()]));
    renderWithProviders(<AdminOrdersPage />);

    await userEvent.click(await screen.findByText('#3'));
    expect(screen.getByText(/2× Caneca/)).toBeInTheDocument();
    expect(screen.getByText('CEP 01000-000')).toBeInTheDocument();
    await userEvent.click(screen.getByText('#3'));
    expect(screen.queryByText(/2× Caneca/)).not.toBeInTheDocument();
  });

  it('lista vazia', async () => {
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([]));
    renderWithProviders(<AdminOrdersPage />);
    expect(await screen.findByText('Nenhum pedido encontrado')).toBeInTheDocument();
  });
});

describe('AdminUsersPage', () => {
  const users = () => page([
    adminUser({ id: 1, name: 'Chefe', email: 'admin@x.com', role: 'ADMIN', orderCount: 0 }),
    adminUser(),
    adminUser({ id: 3, name: 'Bia', email: 'bia@b.com', active: false, role: 'ADMIN', phone: undefined }),
  ]);

  it('a própria conta não tem ações', async () => {
    vi.mocked(adminApi.getAdminUsers).mockResolvedValue(users());
    renderWithProviders(<AdminUsersPage />);

    expect(await screen.findByText('(você)')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /Bloquear|Desbloquear/ })).toHaveLength(2);
  });

  it('bloqueia, desbloqueia e altera perfil', async () => {
    vi.mocked(adminApi.getAdminUsers).mockResolvedValue(users());
    vi.mocked(adminApi.updateUser).mockResolvedValue(adminUser());
    renderWithProviders(<AdminUsersPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Bloquear' }));
    expect(adminApi.updateUser).toHaveBeenCalledWith(2, { active: false });
    await userEvent.click(screen.getByRole('button', { name: 'Desbloquear' }));
    expect(adminApi.updateUser).toHaveBeenCalledWith(3, { active: true });
    await userEvent.click(screen.getByRole('button', { name: 'Tornar admin' }));
    expect(adminApi.updateUser).toHaveBeenCalledWith(2, { role: 'ADMIN' });
    await userEvent.click(screen.getByRole('button', { name: 'Tornar cliente' }));
    expect(adminApi.updateUser).toHaveBeenCalledWith(3, { role: 'CUSTOMER' });
  });

  it('mostra erro do back-end traduzido', async () => {
    vi.mocked(adminApi.getAdminUsers).mockResolvedValue(users());
    vi.mocked(adminApi.updateUser).mockRejectedValue(httpError('You cannot change your own account'));
    renderWithProviders(<AdminUsersPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Bloquear' }));
    expect(await screen.findByText('Você não pode alterar a sua própria conta.')).toBeInTheDocument();
  });

  it('busca e mostra os pedidos do usuário', async () => {
    vi.mocked(adminApi.getAdminUsers).mockResolvedValue(users());
    vi.mocked(adminApi.getUserOrders).mockResolvedValue([order({ id: 77, status: 'SHIPPED' })]);
    renderWithProviders(<AdminUsersPage />);

    await userEvent.type(await screen.findByPlaceholderText(/Buscar por nome/), 'ana{enter}');
    await waitFor(() => expect(adminApi.getAdminUsers).toHaveBeenLastCalledWith(0, 'ana'));

    await userEvent.click(screen.getAllByRole('button', { name: '2' })[0]);
    expect(await screen.findByText('#77')).toBeInTheDocument();
    expect(screen.getByText('Enviado')).toBeInTheDocument();
    expect(adminApi.getUserOrders).toHaveBeenCalledWith(2);

    await userEvent.click(screen.getAllByRole('button', { name: '2' })[0]);
    expect(screen.queryByText('#77')).not.toBeInTheDocument();
  });
});

describe('AdminAuditPage', () => {
  const log = (over = {}) => ({ id: 1, createdAt: '2026-03-15T12:00:00', actorId: 2, actorEmail: 'ana@a.com', action: 'LOGIN_FAILED',
    targetType: 'USER', targetId: '2', details: 'wrong password', ip: '203.0.113.7', ...over });

  it('lista eventos traduzidos, filtra por ação e pagina', async () => {
    vi.mocked(adminApi.getAuditLogs).mockResolvedValue({ ...page([log(), log({ id: 2, actorEmail: null, action: 'NOVA_ACAO', targetType: null })]), totalPages: 2 });
    renderWithProviders(<AdminAuditPage />);

    expect(await screen.findByText('Login recusado')).toBeInTheDocument();
    expect(screen.getAllByText('203.0.113.7')).toHaveLength(2);
    expect(screen.getByText('USER #2')).toBeInTheDocument();
    expect(screen.getByText('sistema')).toBeInTheDocument();       // sem ator = job/PSP
    expect(screen.getByText('NOVA_ACAO')).toBeInTheDocument();     // ação sem tradução aparece crua

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Ação' }), 'PAYMENT_CONFIRMED');
    await waitFor(() => expect(adminApi.getAuditLogs).toHaveBeenLastCalledWith(0, 'PAYMENT_CONFIRMED'));
    await userEvent.click(screen.getByRole('button', { name: 'Próxima página' }));
    await waitFor(() => expect(adminApi.getAuditLogs).toHaveBeenLastCalledWith(1, 'PAYMENT_CONFIRMED'));
  });

  it('vazio e erro', async () => {
    vi.mocked(adminApi.getAuditLogs).mockResolvedValueOnce(page([])).mockRejectedValueOnce(httpError('Access denied'));
    renderWithProviders(<AdminAuditPage />);
    expect(await screen.findByText('Nenhum registro')).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Ação' }), 'LOGOUT');
    expect(await screen.findByRole('alert')).toHaveTextContent('Você não tem permissão');
  });
});
