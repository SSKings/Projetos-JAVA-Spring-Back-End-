import { vi } from 'vitest';
import { useCart } from '../contexts/CartContext';
import type { CartSummary } from '../types';

/** Configura o retorno de useCart (o módulo precisa estar mockado com vi.mock no arquivo de teste). */
export function mockCart(cart: CartSummary, loading = false) {
  const api = {
    cart, loading,
    addItem: vi.fn().mockResolvedValue(undefined),
    updateItem: vi.fn().mockResolvedValue(undefined),
    removeItem: vi.fn().mockResolvedValue(undefined),
    clearCart: vi.fn().mockResolvedValue(undefined),
    refresh: vi.fn().mockResolvedValue(undefined),
  };
  vi.mocked(useCart).mockReturnValue(api);
  return api;
}
