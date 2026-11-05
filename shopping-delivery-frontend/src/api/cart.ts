import api from './axios';
import type { CartSummary } from '../types';

export const getCart = () =>
  api.get<CartSummary>('/api/cart').then(r => r.data);

export const addToCart = (productId: number, quantity: number) =>
  api.post<CartSummary>('/api/cart/items', { productId, quantity }).then(r => r.data);

export const updateCartItem = (itemId: number, quantity: number) =>
  api.put<CartSummary>(`/api/cart/items/${itemId}`, { quantity }).then(r => r.data);

export const removeCartItem = (itemId: number) =>
  api.delete<CartSummary>(`/api/cart/items/${itemId}`).then(r => r.data);

export const clearCart = () =>
  api.delete('/api/cart');
