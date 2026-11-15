import { describe, expect, it } from 'vitest';
import {
  aggregatePurchases, filterOrders, isAwaitingPayment, isSettled, itemsPreview, matchesFilter, normalize, sortPurchases,
  summarizeOrders,
} from './orders';
import { order, payment } from '../test/fixtures';
import type { OrderItem } from '../types';

const item = (over: Partial<OrderItem> = {}): OrderItem => ({
  id: 1, productId: 1, productName: 'Caneca', quantity: 1, unitPrice: 10, subtotal: 10, ...over,
});

const orders = [
  order({ id: 1, status: 'DELIVERED', total: 30, createdAt: '2026-01-10T10:00:00',
    items: [item({ quantity: 3, subtotal: 30 })] }),
  order({ id: 2, status: 'PENDING', total: 45.5, createdAt: '2026-02-10T10:00:00',
    items: [item({ id: 2, quantity: 1, unitPrice: 12, subtotal: 12, productName: 'Caneca Azul' }),
            item({ id: 3, productId: 2, productName: 'Câmera', unitPrice: 33.5, subtotal: 33.5 })] }),
  order({ id: 3, status: 'CANCELLED', total: 999, createdAt: '2026-03-10T10:00:00',
    items: [item({ id: 4, productId: 3, productName: 'TV', unitPrice: 999, subtotal: 999 })] }),
  order({ id: 4, status: 'SHIPPED', total: 0.3, createdAt: '2026-02-20T10:00:00',
    items: [item({ id: 5, productId: 2, productName: 'Câmera', quantity: 2, unitPrice: 0.15, subtotal: 0.3 })] }),
];

describe('filtros', () => {
  it('normalize ignora acento e caixa', () => {
    expect(normalize(' Câmera ')).toBe('camera');
  });

  it('matchesFilter agrupa PENDING/CONFIRMED/SHIPPED como "em andamento"', () => {
    expect(matchesFilter('CONFIRMED', 'IN_PROGRESS')).toBe(true);
    expect(matchesFilter('DELIVERED', 'IN_PROGRESS')).toBe(false);
    expect(matchesFilter('CANCELLED', 'CANCELLED')).toBe(true);
    expect(matchesFilter('CANCELLED', 'ALL')).toBe(true);
  });

  it('filterOrders por status, número (#id) e nome de produto', () => {
    expect(filterOrders(orders, 'ALL').map(o => o.id)).toEqual([1, 2, 3, 4]);
    expect(filterOrders(orders, 'IN_PROGRESS').map(o => o.id)).toEqual([2, 4]);
    expect(filterOrders(orders, 'ALL', '#3').map(o => o.id)).toEqual([3]);
    expect(filterOrders(orders, 'ALL', 'camera').map(o => o.id)).toEqual([2, 4]);
    expect(filterOrders(orders, 'DELIVERED', 'camera')).toEqual([]);
  });
});

describe('summarizeOrders', () => {
  it('soma o gasto sem pedidos cancelados e conta por grupo (arredonda centavos)', () => {
    expect(summarizeOrders(orders)).toEqual({ count: 4, totalSpent: 75.8, inProgress: 2, delivered: 1, cancelled: 1 });
    expect(summarizeOrders([])).toEqual({ count: 0, totalSpent: 0, inProgress: 0, delivered: 0, cancelled: 0 });
  });
});

describe('pagamento', () => {
  it('isSettled: não cancelado e pago (ou sem pagamento, pedidos antigos)', () => {
    expect(isSettled(order())).toBe(true);
    expect(isSettled(order({ payment: null }))).toBe(true);
    expect(isSettled(order({ payment: payment({ status: 'PAID' }) }))).toBe(true);
    expect(isSettled(order({ payment: payment() }))).toBe(false);
    expect(isSettled(order({ status: 'CANCELLED', payment: payment({ status: 'PAID' }) }))).toBe(false);
  });

  it('isAwaitingPayment', () => {
    expect(isAwaitingPayment(order({ payment: payment() }))).toBe(true);
    expect(isAwaitingPayment(order({ payment: payment({ status: 'EXPIRED' }) }))).toBe(false);
    expect(isAwaitingPayment(order())).toBe(false);
  });

  it('Pix pendente não entra no total gasto nem no histórico de compras', () => {
    const unpaid = order({ id: 9, total: 500, payment: payment(), items: [item({ productId: 77, subtotal: 500 })] });
    expect(summarizeOrders([unpaid]).totalSpent).toBe(0);
    expect(summarizeOrders([unpaid]).inProgress).toBe(1);
    expect(aggregatePurchases([unpaid])).toEqual([]);
  });
});

describe('itemsPreview', () => {
  it('lista até 2 nomes e resume o resto', () => {
    expect(itemsPreview(orders[1])).toBe('Caneca Azul, Câmera');
    expect(itemsPreview(order({ items: [item(), item({ productName: 'B' }), item({ productName: 'C' }), item({ productName: 'D' })] })))
      .toBe('Caneca, B e mais 2');
  });
});

describe('aggregatePurchases', () => {
  const result = aggregatePurchases(orders);
  const byId = (id: number) => result.find(p => p.productId === id)!;

  it('ignora pedidos cancelados', () => {
    expect(result.map(p => p.productId).sort()).toEqual([1, 2]);
  });

  it('agrupa por productId e usa o snapshot mais recente de nome e preço', () => {
    expect(byId(1)).toEqual({
      productId: 1, productName: 'Caneca Azul', quantity: 4, totalSpent: 42, lastUnitPrice: 12,
      lastPurchaseAt: '2026-02-10T10:00:00', lastOrderId: 2, orderCount: 2,
    });
    expect(byId(2)).toMatchObject({ quantity: 3, totalSpent: 33.8, lastOrderId: 4, orderCount: 2, lastUnitPrice: 0.15 });
  });

  it('o mesmo produto repetido num pedido conta como um pedido só', () => {
    const [p] = aggregatePurchases([order({ items: [item(), item({ id: 9 })] })]);
    expect(p).toMatchObject({ quantity: 2, orderCount: 1 });
  });

  it('sortPurchases por recência, quantidade e gasto (empate pelo nome)', () => {
    expect(sortPurchases(result, 'recent').map(p => p.productId)).toEqual([2, 1]);
    expect(sortPurchases(result, 'quantity').map(p => p.productId)).toEqual([1, 2]);
    expect(sortPurchases(result, 'spent').map(p => p.productId)).toEqual([1, 2]);
    const tie = [{ ...byId(1), productName: 'B', quantity: 1, totalSpent: 1 }, { ...byId(2), productName: 'A', quantity: 1, totalSpent: 1 }];
    expect(sortPurchases(tie, 'quantity').map(p => p.productName)).toEqual(['A', 'B']);
    expect(sortPurchases(tie, 'spent').map(p => p.productName)).toEqual(['A', 'B']);
  });
});
