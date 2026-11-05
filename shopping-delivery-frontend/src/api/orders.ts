import api from './axios';
import type { Order, Payment, PaymentMethod } from '../types';

export const checkout = (addressId: number, paymentMethod: PaymentMethod = 'PIX') =>
  api.post<Order>('/api/orders/checkout', { addressId, paymentMethod }).then(r => r.data);

/** Cancelamento pelo cliente antes do envio. */
export const cancelOrder = (id: number) => api.post<Order>(`/api/orders/${id}/cancel`).then(r => r.data);

/** Direito de arrependimento (até 7 dias da entrega). O motivo é opcional. */
export const requestReturn = (id: number, reason?: string) =>
  api.post<Order>(`/api/orders/${id}/return`, { reason: reason || undefined }).then(r => r.data);

/** Usado no polling da tela de pagamento. */
export const getPayment = (orderId: number) =>
  api.get<Payment>(`/api/orders/${orderId}/payment`).then(r => r.data);

export const getOrders = () =>
  api.get<Order[]>('/api/orders').then(r => r.data);

export const getOrder = (id: number) =>
  api.get<Order>(`/api/orders/${id}`).then(r => r.data);
