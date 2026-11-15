import { createContext, useContext } from 'react';
import type { CartSummary } from '../types';

// Contexto e hook ficam aqui; o componente CartProvider fica em CartProvider.tsx (ver AuthContext.ts).

export interface CartContextType {
  cart: CartSummary;
  loading: boolean;
  addItem: (productId: number, quantity: number) => Promise<void>;
  updateItem: (itemId: number, quantity: number) => Promise<void>;
  removeItem: (itemId: number) => Promise<void>;
  clearCart: () => Promise<void>;
  refresh: () => Promise<void>;
}

export const CartContext = createContext<CartContextType | null>(null);

export function useCart() {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart must be used inside CartProvider');
  return ctx;
}
