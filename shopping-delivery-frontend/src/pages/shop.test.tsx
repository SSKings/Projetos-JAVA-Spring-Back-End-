import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ProductListPage from './ProductListPage';
import ProductDetailPage from './ProductDetailPage';
import ProductCard from '../components/ProductCard';
import * as productsApi from '../api/products';
import { loginAs, page, renderWithProviders } from '../test/utils';
import { cart, product } from '../test/fixtures';
import type { Page, Product } from '../types';
import { mockCart } from '../test/cartMock';

vi.mock('../api/products');
vi.mock('../contexts/CartContext', () => ({ useCart: vi.fn() }));

beforeEach(() => {
  vi.resetAllMocks();
  mockCart(cart());
});

describe('ProductListPage', () => {
  it('lista produtos e busca por termo', async () => {
    vi.mocked(productsApi.getProducts).mockResolvedValue(page([product(), product({ id: 2, name: 'Mouse' })]));
    renderWithProviders(<ProductListPage />);

    expect(await screen.findByText('Mouse')).toBeInTheDocument();
    expect(productsApi.getProducts).toHaveBeenCalledWith(0, 12, undefined);

    await userEvent.type(screen.getByPlaceholderText('Buscar produtos...'), 'cane');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    await waitFor(() => expect(productsApi.getProducts).toHaveBeenLastCalledWith(0, 12, 'cane'));
  });

  it('mostra estado vazio', async () => {
    vi.mocked(productsApi.getProducts).mockResolvedValue(page([]));
    renderWithProviders(<ProductListPage />);
    expect(await screen.findByText('Nenhum produto encontrado')).toBeInTheDocument();
  });

  it('pagina', async () => {
    vi.mocked(productsApi.getProducts).mockResolvedValue(page([product()], 0, 3));
    renderWithProviders(<ProductListPage />);
    await screen.findByText('Caneca');

    const [prev, next] = screen.getAllByRole('button').filter(b => b.textContent === '');
    expect(prev).toBeDisabled();
    vi.mocked(productsApi.getProducts).mockResolvedValue(page([product()], 1, 3));
    await userEvent.click(next);
    await waitFor(() => expect(productsApi.getProducts).toHaveBeenLastCalledWith(1, 12, undefined));
  });

  it('resposta atrasada de uma busca antiga não sobrescreve a mais nova', async () => {
    let resolveOld!: (v: Page<Product>) => void;
    vi.mocked(productsApi.getProducts)
      .mockReturnValueOnce(new Promise<Page<Product>>(r => { resolveOld = r; }))  // carga inicial: lenta
      .mockResolvedValueOnce(page([product({ id: 2, name: 'Mouse' })]));     // busca "mouse": rápida
    renderWithProviders(<ProductListPage />);

    await userEvent.type(screen.getByPlaceholderText('Buscar produtos...'), 'mouse');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(await screen.findByText('Mouse')).toBeInTheDocument();

    // a carga inicial termina por último: a tela continua com o resultado da busca
    await act(async () => resolveOld(page([product({ id: 1, name: 'Caneca' })])));
    expect(screen.getByText('Mouse')).toBeInTheDocument();
    expect(screen.queryByText('Caneca')).not.toBeInTheDocument();
  });

  it('falha da API encerra o carregamento (sem esqueleto infinito)', async () => {
    vi.mocked(productsApi.getProducts).mockRejectedValue(new Error('rede'));
    const { container } = renderWithProviders(<ProductListPage />);
    expect(await screen.findByText('Nenhum produto encontrado')).toBeInTheDocument();
    expect(container.querySelector('.animate-pulse')).toBeNull();
  });

  it('falha ao paginar mantém os produtos que estavam na tela', async () => {
    vi.mocked(productsApi.getProducts).mockResolvedValueOnce(page([product()], 0, 3));
    renderWithProviders(<ProductListPage />);
    await screen.findByText('Caneca');

    vi.mocked(productsApi.getProducts).mockRejectedValueOnce(new Error('rede'));
    const [, next] = screen.getAllByRole('button').filter(b => b.textContent === '');
    await userEvent.click(next);
    await waitFor(() => expect(productsApi.getProducts).toHaveBeenLastCalledWith(1, 12, undefined));
    expect(await screen.findByText('Caneca')).toBeInTheDocument();
    expect(screen.getByText('1', { selector: 'strong' })).toBeInTheDocument();  // continua na página 1
  });

  it('buscar o mesmo termo de novo não recarrega', async () => {
    vi.mocked(productsApi.getProducts).mockResolvedValue(page([product()]));
    renderWithProviders(<ProductListPage />);
    await screen.findByText('Caneca');
    const calls = vi.mocked(productsApi.getProducts).mock.calls.length;
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(vi.mocked(productsApi.getProducts).mock.calls.length).toBe(calls);
  });
});

describe('ProductCard', () => {
  it('visitante não vê botão de carrinho', () => {
    renderWithProviders(<ProductCard product={product()} />);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('cliente adiciona ao carrinho', async () => {
    loginAs('CUSTOMER');
    const cartApi = mockCart(cart());
    renderWithProviders(<ProductCard product={product()} />);

    await userEvent.click(screen.getByRole('button'));
    expect(cartApi.addItem).toHaveBeenCalledWith(1, 1);
  });

  it('sem estoque mostra selo e esconde botão', () => {
    loginAs('CUSTOMER');
    renderWithProviders(<ProductCard product={product({ stockQuantity: 0, imageUrl: '' })} />);
    expect(screen.getByText('Sem estoque')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});

describe('ProductDetailPage', () => {
  const renderDetail = () => renderWithProviders(<ProductDetailPage />, { route: '/products/1', path: '/products/:id' });

  it('cliente escolhe quantidade (limitada ao estoque) e adiciona', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    loginAs('CUSTOMER');
    const cartApi = mockCart(cart());
    vi.mocked(productsApi.getProduct).mockResolvedValue(product({ stockQuantity: 2 }));
    renderDetail();

    await screen.findByRole('heading', { name: 'Caneca' });
    await userEvent.click(screen.getByRole('button', { name: '+' }));
    await userEvent.click(screen.getByRole('button', { name: '+' }));
    await userEvent.click(screen.getByRole('button', { name: '−' }));
    await userEvent.click(screen.getByRole('button', { name: '+' }));
    await userEvent.click(screen.getByRole('button', { name: /Adicionar ao Carrinho/ }));

    expect(cartApi.addItem).toHaveBeenCalledWith(1, 2);
    expect(await screen.findByText('Adicionado!')).toBeInTheDocument();
    await act(() => vi.advanceTimersByTimeAsync(2100));
    expect(screen.getByText('Adicionar ao Carrinho')).toBeInTheDocument();
    vi.useRealTimers();
  });

  it('quantidade para em 10 mesmo com estoque maior (limite anti-açambarcamento)', async () => {
    loginAs('CUSTOMER');
    const cartApi = mockCart(cart());
    vi.mocked(productsApi.getProduct).mockResolvedValue(product({ stockQuantity: 50 }));
    renderDetail();

    await screen.findByRole('heading', { name: 'Caneca' });
    const plus = screen.getByRole('button', { name: '+' });
    for (let i = 0; i < 12; i++) await userEvent.click(plus);
    expect(plus).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: /Adicionar ao Carrinho/ }));
    expect(cartApi.addItem).toHaveBeenCalledWith(1, 10);
  });

  it('visitante é convidado a fazer login', async () => {
    vi.mocked(productsApi.getProduct).mockResolvedValue(product({ stockQuantity: 0, imageUrl: '' }));
    renderDetail();
    expect(await screen.findByText('Faça login')).toBeInTheDocument();
    expect(screen.getByText('Sem estoque')).toBeInTheDocument();
  });

  it('produto inexistente volta para a home', async () => {
    vi.mocked(productsApi.getProduct).mockRejectedValue(new Error('404'));
    renderDetail();
    expect(await screen.findByText('HOME PAGE')).toBeInTheDocument();
  });
});
