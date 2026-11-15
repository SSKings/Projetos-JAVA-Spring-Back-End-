import api from './axios';
import type {
  AuditLog, AdminOrder, AdminProduct, AdminUser, DashboardStats, Order, OrderStatus, Page, ProductInput, Role,
} from '../types';

// Dashboard
export const getDashboard = () =>
  api.get<DashboardStats>('/api/admin/dashboard').then(r => r.data);

// Produtos
export const getAdminProducts = (page = 0, search?: string) =>
  api.get<Page<AdminProduct>>('/api/admin/products', { params: { page, size: 10, search: search || undefined } })
    .then(r => r.data);

export const createProduct = (data: ProductInput) =>
  api.post<AdminProduct>('/api/admin/products', data).then(r => r.data);

export const updateProduct = (id: number, data: ProductInput) =>
  api.put<AdminProduct>(`/api/admin/products/${id}`, data).then(r => r.data);

export const deactivateProduct = (id: number) =>
  api.delete(`/api/admin/products/${id}`);

// Pedidos
export const getAdminOrders = (page = 0, status?: OrderStatus) =>
  api.get<Page<AdminOrder>>('/api/admin/orders', { params: { page, size: 10, status } }).then(r => r.data);

/** Registra que o Pix foi devolvido (estorno pendente ou arrependimento). */
export const refundPayment = (id: number, password: string, mfaCode?: string) =>
  api.post<AdminOrder>(`/api/admin/orders/${id}/payment/refund`, { password, mfaCode }).then(r => r.data);

export const getAuditLogs = (page = 0, action?: string) =>
  api.get<Page<AuditLog>>('/api/admin/audit', { params: { page, size: 20, action: action || undefined } }).then(r => r.data);

/** Confirmação manual do Pix, depois de conferir o extrato. Exige a senha (e o MFA) do admin — revisão 5, R5-2. */
export const confirmPayment = (id: number, password: string, mfaCode?: string) =>
  api.post<AdminOrder>(`/api/admin/orders/${id}/payment/confirm`, { password, mfaCode }).then(r => r.data);

export const updateOrderStatus = (id: number, status: OrderStatus) =>
  api.patch<AdminOrder>(`/api/admin/orders/${id}/status`, { status }).then(r => r.data);

// Usuários
export const getAdminUsers = (page = 0, search?: string) =>
  api.get<Page<AdminUser>>('/api/admin/users', { params: { page, size: 10, search: search || undefined } })
    .then(r => r.data);

/** password/mfaCode: do admin que está agindo; exigidos para trocar papel ou alterar outro admin (revisão 4, R4-3). */
export const updateUser = (id: number, data: { active?: boolean; role?: Role; password?: string; mfaCode?: string }) =>
  api.patch<AdminUser>(`/api/admin/users/${id}`, data).then(r => r.data);

export const getUserOrders = (id: number) =>
  api.get<Order[]>(`/api/admin/users/${id}/orders`).then(r => r.data);
