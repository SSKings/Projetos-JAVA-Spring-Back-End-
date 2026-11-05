import api from './axios';
import type { Order } from '../types';

export const checkout = (addressId: number) =>
  api.post<Order>('/api/orders/checkout', { addressId }).then(r => r.data);

export const getOrders = () =>
  api.get<Order[]>('/api/orders').then(r => r.data);

export const getOrder = (id: number) =>
  api.get<Order>(`/api/orders/${id}`).then(r => r.data);
