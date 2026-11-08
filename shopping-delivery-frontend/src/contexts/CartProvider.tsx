import { useEffect, useState, type ReactNode } from 'react';
import * as cartApi from '../api/cart';
import type { CartSummary } from '../types';
import { useAuth } from './AuthContext';
import { CartContext } from './CartContext';

const emptyCart: CartSummary = { items: [], total: 0, itemCount: 0 };

export function CartProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  const [cart, setCart] = useState<CartSummary>(emptyCart);
  const [loading, setLoading] = useState(false);

  const refresh = async () => {
    if (!isAuthenticated) { setCart(emptyCart); return; }
    setLoading(true);
    try {
      const data = await cartApi.getCart();
      setCart(data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { refresh(); }, [isAuthenticated]); // eslint-disable-line

  const addItem = async (productId: number, quantity: number) => {
    const data = await cartApi.addToCart(productId, quantity);
    setCart(data);
  };

  const updateItem = async (itemId: number, quantity: number) => {
    const data = await cartApi.updateCartItem(itemId, quantity);
    setCart(data);
  };

  const removeItem = async (itemId: number) => {
    const data = await cartApi.removeCartItem(itemId);
    setCart(data);
  };

  const clear = async () => {
    await cartApi.clearCart();
    setCart(emptyCart);
  };

  return (
    <CartContext.Provider value={{ cart, loading, addItem, updateItem, removeItem, clearCart: clear, refresh }}>
      {children}
    </CartContext.Provider>
  );
}
