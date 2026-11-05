import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdminDashboardPage from './AdminDashboardPage';
import AdminProductsPage from './AdminProductsPage';
import AdminOrdersPage from './AdminOrdersPage';
import AdminUsersPage from './AdminUsersPage';
import * as adminApi from '../../api/admin';
import { loginAs, page, renderWithProviders } from '../../test/utils';
import { adminOrder, adminProduct, adminUser, order } from '../../test/fixtures';

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
  it('filtra por status e oferece só as transições válidas', async () => {
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([
      adminOrder(), adminOrder({ id: 4, status: 'DELIVERED' }),
    ]));
    renderWithProviders(<AdminOrdersPage />);

    expect(await screen.findByText('#3')).toBeInTheDocument();
    expect(screen.getByText('Finalizado')).toBeInTheDocument();
    const select = screen.getByRole('combobox');
    const options = within(select).getAllByRole('option').map(o => o.textContent);
    expect(options).toEqual(['Mover para…', 'Confirmado', 'Cancelado']);

    await userEvent.click(screen.getByRole('button', { name: 'Pendente' }));
    await waitFor(() => expect(adminApi.getAdminOrders).toHaveBeenLastCalledWith(0, 'PENDING'));
  });

  it('altera status e recarrega', async () => {
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([adminOrder()]));
    vi.mocked(adminApi.updateOrderStatus).mockResolvedValue(adminOrder({ status: 'CONFIRMED' }));
    renderWithProviders(<AdminOrdersPage />);

    await userEvent.selectOptions(await screen.findByRole('combobox'), 'CONFIRMED');
    expect(adminApi.updateOrderStatus).toHaveBeenCalledWith(3, 'CONFIRMED');
    await waitFor(() => expect(adminApi.getAdminOrders).toHaveBeenCalledTimes(2));
  });

  it('mostra erro de transição inválida', async () => {
    vi.mocked(adminApi.getAdminOrders).mockResolvedValue(page([adminOrder()]));
    vi.mocked(adminApi.updateOrderStatus).mockRejectedValue(httpError('Cannot change order status'));
    renderWithProviders(<AdminOrdersPage />);

    await userEvent.selectOptions(await screen.findByRole('combobox'), 'CANCELLED');
    expect(await screen.findByText('Cannot change order status')).toBeInTheDocument();
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
