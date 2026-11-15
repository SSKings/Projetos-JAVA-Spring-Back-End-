import type { Address, AdminOrder, AdminProduct, AdminUser, CartSummary, Order, OrderStatus, Payment, Product } from '../types';

export const product = (over: Partial<Product> = {}): Product => ({
  id: 1, name: 'Caneca', description: 'Cerâmica', imageUrl: 'http://img/caneca.png', price: 29.9, stockQuantity: 5, ...over,
});

export const adminProduct = (over: Partial<AdminProduct> = {}): AdminProduct => ({ ...product(), active: true, ...over });

export const address = (over: Partial<Address> = {}): Address => ({
  id: 7, zipCode: '01000-000', street: 'Rua A', number: '10', complement: 'Apto 2',
  neighborhood: 'Centro', city: 'São Paulo', state: 'SP', ...over,
});

/** BR Code de exemplo (formato real, valor 59.80). */
export const PIX_CODE = '00020101021226360014br.gov.bcb.pix0114pix@loja.com.br520400005303986540559.805802BR5908ShopEasy6009Sao Paulo6229052500000000000000000000000016304ABCD';

export const payment = (over: Partial<Payment> = {}): Payment => ({
  method: 'PIX', status: 'PENDING', amount: 59.8, txid: 'TX0000000000000000000000001', pixCopyPaste: PIX_CODE,
  createdAt: '2026-03-15T12:00:00', expiresAt: '2026-03-15T12:30:00', paidAt: null, expiresInSeconds: 1800, ...over,
});

export const order = (over: Partial<Order> = {}): Order => ({
  id: 3, status: 'PENDING', total: 59.8, createdAt: '2026-03-15T12:00:00', address: address(),
  items: [{ id: 1, productId: 1, productName: 'Caneca', quantity: 2, unitPrice: 29.9, subtotal: 59.8 }], ...over,
});

/** Mesmo resultado de OrderStatus.nextStatuses() no back-end (dados de teste). */
export const NEXT_STATUSES: Record<OrderStatus, OrderStatus[]> = {
  PENDING: ['CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'],
  CONFIRMED: ['SHIPPED', 'DELIVERED', 'CANCELLED'],
  SHIPPED: ['DELIVERED'],
  DELIVERED: [],
  CANCELLED: [],
};

export const adminOrder = (over: Partial<AdminOrder> = {}): AdminOrder => ({
  ...order(), customerId: 2, customerName: 'Ana', customerEmail: 'ana@a.com',
  nextStatuses: NEXT_STATUSES[over.status ?? 'PENDING'], ...over,
});

export const adminUser = (over: Partial<AdminUser> = {}): AdminUser => ({
  id: 2, name: 'Ana', email: 'ana@a.com', phone: '1199999', role: 'CUSTOMER', active: true,
  createdAt: '2026-01-10T10:00:00', orderCount: 2, ...over,
});

export const cart = (over: Partial<CartSummary> = {}): CartSummary => ({
  items: [{ id: 11, productId: 1, productName: 'Caneca', productImageUrl: 'http://img', unitPrice: 29.9, quantity: 2, subtotal: 59.8 }],
  total: 59.8, itemCount: 2, ...over,
});
