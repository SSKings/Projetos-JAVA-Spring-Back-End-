import api from './axios';
import type { Page, Product } from '../types';

export const getProducts = (page = 0, size = 12, search?: string) =>
  api.get<Page<Product>>('/api/products', { params: { page, size, search } }).then(r => r.data);

export const getProduct = (id: number) =>
  api.get<Product>(`/api/products/${id}`).then(r => r.data);
