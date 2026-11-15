import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { AuthProvider } from './AuthProvider';
import { CartProvider } from './CartProvider';
import { useCart } from './CartContext';
import * as cartApi from '../api/cart';
import { loginAs } from '../test/utils';

vi.mock('../api/cart');

const wrapper = ({ children }: { children: ReactNode }) => (
  <AuthProvider><CartProvider>{children}</CartProvider></AuthProvider>
);

const summary = (itemCount: number, total = 10) => ({ items: [], total, itemCount });

describe('CartContext', () => {
  beforeEach(() => vi.resetAllMocks());

  it('deslogado: carrinho vazio e nenhuma chamada à API', () => {
    const { result } = renderHook(() => useCart(), { wrapper });
    expect(result.current.cart.itemCount).toBe(0);
    expect(cartApi.getCart).not.toHaveBeenCalled();
  });

  it('logado: carrega o carrinho e aplica as operações', async () => {
    loginAs('CUSTOMER');
    vi.mocked(cartApi.getCart).mockResolvedValue(summary(1));
    vi.mocked(cartApi.addToCart).mockResolvedValue(summary(3));
    vi.mocked(cartApi.updateCartItem).mockResolvedValue(summary(2));
    vi.mocked(cartApi.removeCartItem).mockResolvedValue(summary(1));
    vi.mocked(cartApi.clearCart).mockResolvedValue({} as never);

    const { result } = renderHook(() => useCart(), { wrapper });
    await waitFor(() => expect(result.current.cart.itemCount).toBe(1));

    await act(() => result.current.addItem(1, 2));
    expect(result.current.cart.itemCount).toBe(3);
    await act(() => result.current.updateItem(1, 1));
    expect(result.current.cart.itemCount).toBe(2);
    await act(() => result.current.removeItem(1));
    expect(result.current.cart.itemCount).toBe(1);
    await act(() => result.current.clearCart());
    expect(result.current.cart).toEqual({ items: [], total: 0, itemCount: 0 });
  });

  it('useCart fora do provider lança erro', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => renderHook(() => useCart())).toThrow(/CartProvider/);
  });
});
