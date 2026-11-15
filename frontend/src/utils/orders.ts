import type { Order, OrderStatus } from '../types';

/** Agrupamentos usados nos filtros do histórico de pedidos. */
export type OrderFilter = 'ALL' | 'IN_PROGRESS' | 'DELIVERED' | 'CANCELLED';

export const IN_PROGRESS_STATUSES: OrderStatus[] = ['PENDING', 'CONFIRMED', 'SHIPPED'];

/** Caminho "feliz" do pedido, usado na linha do tempo do detalhe. CANCELLED é tratado à parte. */
export const ORDER_FLOW: OrderStatus[] = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED'];

const cents = (n: number) => Math.round(n * 100) / 100;

/** Pedido que conta como compra: não cancelado e pago (pedidos antigos sem pagamento contam como pagos). */
export const isSettled = (o: Order) => o.status !== 'CANCELLED' && (!o.payment || o.payment.status === 'PAID');

export const isAwaitingPayment = (o: Order) => o.payment?.status === 'PENDING';

/** Remove acentos e caixa para buscas ("Câmera" casa com "camera"). */
export const normalize = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();

export function matchesFilter(status: OrderStatus, filter: OrderFilter): boolean {
  if (filter === 'ALL') return true;
  if (filter === 'IN_PROGRESS') return IN_PROGRESS_STATUSES.includes(status);
  return status === filter;
}

/** Filtra por grupo de status e por texto (número do pedido ou nome de produto). */
export function filterOrders(orders: Order[], filter: OrderFilter, query = ''): Order[] {
  const q = normalize(query).replace(/^#/, '');
  return orders.filter(o =>
    matchesFilter(o.status, filter) &&
    (!q || String(o.id) === q || o.items.some(i => normalize(i.productName).includes(q))),
  );
}

export interface OrdersSummary {
  count: number;
  /** Soma dos pedidos pagos e não cancelados (mesma regra do faturamento no back-end). */
  totalSpent: number;
  inProgress: number;
  delivered: number;
  cancelled: number;
}

export function summarizeOrders(orders: Order[]): OrdersSummary {
  return orders.reduce<OrdersSummary>((acc, o) => ({
    count: acc.count + 1,
    totalSpent: isSettled(o) ? cents(acc.totalSpent + o.total) : acc.totalSpent,
    inProgress: acc.inProgress + (IN_PROGRESS_STATUSES.includes(o.status) ? 1 : 0),
    delivered: acc.delivered + (o.status === 'DELIVERED' ? 1 : 0),
    cancelled: acc.cancelled + (o.status === 'CANCELLED' ? 1 : 0),
  }), { count: 0, totalSpent: 0, inProgress: 0, delivered: 0, cancelled: 0 });
}

/** "Caneca, Camiseta e mais 2" */
export function itemsPreview(order: Order, max = 2): string {
  const names = order.items.map(i => i.productName);
  if (names.length <= max) return names.join(', ');
  return `${names.slice(0, max).join(', ')} e mais ${names.length - max}`;
}

export interface PurchasedProduct {
  productId: number;
  /** Nome do snapshot mais recente (o produto pode ter sido renomeado depois). */
  productName: string;
  quantity: number;
  totalSpent: number;
  lastUnitPrice: number;
  lastPurchaseAt: string;
  lastOrderId: number;
  orderCount: number;
}

/**
 * Consolida por produto os itens dos pedidos pagos e não cancelados.
 * Agrupa por productId — e não pelo nome — porque o nome é um snapshot que pode mudar.
 */
export function aggregatePurchases(orders: Order[]): PurchasedProduct[] {
  const byProduct = new Map<number, PurchasedProduct & { orders: Set<number> }>();
  const chronological = [...orders]
    .filter(isSettled)
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt));

  for (const order of chronological) {
    for (const item of order.items) {
      const current = byProduct.get(item.productId);
      const base = current ?? {
        productId: item.productId, productName: item.productName, quantity: 0, totalSpent: 0,
        lastUnitPrice: item.unitPrice, lastPurchaseAt: order.createdAt, lastOrderId: order.id,
        orderCount: 0, orders: new Set<number>(),
      };
      base.orders.add(order.id);
      byProduct.set(item.productId, {
        ...base,
        productName: item.productName,
        quantity: base.quantity + item.quantity,
        totalSpent: cents(base.totalSpent + item.subtotal),
        lastUnitPrice: item.unitPrice,
        lastPurchaseAt: order.createdAt,
        lastOrderId: order.id,
        orderCount: base.orders.size,
      });
    }
  }

  return [...byProduct.values()].map(({ orders: _orders, ...p }) => p);
}

export type PurchaseSort = 'recent' | 'quantity' | 'spent';

export function sortPurchases(list: PurchasedProduct[], sort: PurchaseSort): PurchasedProduct[] {
  const sorted = [...list];
  if (sort === 'quantity') sorted.sort((a, b) => b.quantity - a.quantity || a.productName.localeCompare(b.productName));
  else if (sort === 'spent') sorted.sort((a, b) => b.totalSpent - a.totalSpent || a.productName.localeCompare(b.productName));
  else sorted.sort((a, b) => b.lastPurchaseAt.localeCompare(a.lastPurchaseAt));
  return sorted;
}
