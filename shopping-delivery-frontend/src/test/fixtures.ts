import type { Address, AdminOrder, AdminProduct, AdminUser, CartSummary, Order, Product } from '../types';

export const product = (over: Partial<Product> = {}): Product => ({
  id: 1, name: 'Caneca', description: 'Cerâmica', imageUrl: 'http://img/caneca.png', price: 29.9, stockQuantity: 5, ...over,
});

export const adminProduct = (over: Partial<AdminProduct> = {}): AdminProduct => ({ ...product(), active: true, ...over });

export const address = (over: Partial<Address> = {}): Address => ({
  id: 7, zipCode: '01000-000', street: 'Rua A', number: '10', complement: 'Apto 2',
  neighborhood: 'Centro', city: 'São Paulo', state: 'SP', ...over,
});

export const order = (over: Partial<Order> = {}): Order => ({
  id: 3, status: 'PENDING', total: 59.8, createdAt: '2026-03-15T12:00:00', address: address(),
  items: [{ id: 1, productName: 'Caneca', quantity: 2, unitPrice: 29.9, subtotal: 59.8 }], ...over,
});

export const adminOrder = (over: Partial<AdminOrder> = {}): AdminOrder => ({
  ...order(), customerId: 2, customerName: 'Ana', customerEmail: 'ana@a.com', ...over,
});

export const adminUser = (over: Partial<AdminUser> = {}): AdminUser => ({
  id: 2, name: 'Ana', email: 'ana@a.com', phone: '1199999', role: 'CUSTOMER', active: true,
  createdAt: '2026-01-10T10:00:00', orderCount: 2, ...over,
});

export const cart = (over: Partial<CartSummary> = {}): CartSummary => ({
  items: [{ id: 11, productId: 1, productName: 'Caneca', productImageUrl: 'http://img', unitPrice: 29.9, quantity: 2, subtotal: 59.8 }],
  total: 59.8, itemCount: 2, ...over,
});
