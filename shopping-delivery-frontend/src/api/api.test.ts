import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios';
import api from './axios';
import * as admin from './admin';
import * as auth from './auth';
import * as cart from './cart';
import * as orders from './orders';
import * as products from './products';
import * as addresses from './addresses';
import * as account from './account';

let calls: InternalAxiosRequestConfig[] = [];
let respond: (c: InternalAxiosRequestConfig) => { status: number; data: unknown } = () => ({ status: 200, data: { ok: true } });

const fakeAdapter: AxiosAdapter = async config => {
  calls.push(config);
  const { status, data } = respond(config);
  const response = { data, status, statusText: '', headers: {}, config };
  if (status >= 400) {
    return Promise.reject(Object.assign(new Error('fail'), { response, config, isAxiosError: true }));
  }
  return response;
};

const last = () => calls[calls.length - 1];
const body = () => JSON.parse(last().data as string);

beforeEach(() => {
  calls = [];
  respond = () => ({ status: 200, data: { ok: true } });
  api.defaults.adapter = fakeAdapter;
});

afterEach(() => vi.unstubAllGlobals());

describe('axios instance', () => {
  it('envia o token salvo no header Authorization', async () => {
    localStorage.setItem('token', 'abc');
    await api.get('/x');
    expect(last().headers.Authorization).toBe('Bearer abc');
  });

  it('não envia Authorization sem token', async () => {
    await api.get('/x');
    expect(last().headers.Authorization).toBeUndefined();
  });

  it('em 401 (token expirado ou revogado) limpa a sessão e redireciona para /login', async () => {
    const status = 401;
    const location = { href: '/admin' };
    vi.stubGlobal('location', location);
    localStorage.setItem('token', 'abc');
    localStorage.setItem('user', '{}');
    respond = () => ({ status, data: {} });

    await expect(api.get('/x')).rejects.toBeTruthy();
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
    expect(location.href).toBe('/login');
  });

  it.each([400, 403])('em %i mantém a sessão (403 = sem permissão, não é sessão inválida)', async status => {
    localStorage.setItem('token', 'abc');
    respond = () => ({ status, data: { message: 'bad' } });
    await expect(api.get('/x')).rejects.toBeTruthy();
    expect(localStorage.getItem('token')).toBe('abc');
  });
});

describe('módulos de API chamam as rotas corretas', () => {
  it.each([
    ['auth.register',      () => auth.register({ name: 'A', email: 'a@a', password: 'senha123', acceptTerms: true }), 'post', '/api/auth/register'],
    ['auth.logout',        () => auth.logout(),                                                  'post', '/api/auth/logout'],
    ['account.get',        () => account.getProfile(),                                           'get',  '/api/account'],
    ['account.update',     () => account.updateProfile({ name: 'A' }),                           'put',  '/api/account'],
    ['account.export',     () => account.exportData(),                                           'get',  '/api/account/data-export'],
    ['account.delete',     () => account.deleteAccount('pw'),                                    'post', '/api/account/delete'],
    ['account.mfaSetup',   () => account.setupMfa('pw'),                                         'post', '/api/account/mfa/setup'],
    ['account.password',   () => account.changePassword('pw', 'nova1234'),                       'post', '/api/account/password'],
    ['account.mfaEnable',  () => account.enableMfa('123456'),                                    'post', '/api/account/mfa/enable'],
    ['account.mfaDisable', () => account.disableMfa('pw', '123456'),                             'post', '/api/account/mfa/disable'],
    ['orders.cancel',      () => orders.cancelOrder(2),                                          'post', '/api/orders/2/cancel'],
    ['orders.return',      () => orders.requestReturn(2, 'motivo'),                              'post', '/api/orders/2/return'],
    ['admin.refund',       () => admin.refundPayment(5, 'pw'),                                   'post', '/api/admin/orders/5/payment/refund'],
    ['admin.audit',        () => admin.getAuditLogs(1, 'LOGIN_FAILED'),                          'get',  '/api/admin/audit'],
    ['auth.login',         () => auth.login({ email: 'a@a', password: 'x' }),                   'post', '/api/auth/login'],
    ['products.list',      () => products.getProducts(1, 12, 'tv'),                             'get',  '/api/products'],
    ['products.get',       () => products.getProduct(3),                                         'get',  '/api/products/3'],
    ['cart.get',           () => cart.getCart(),                                                 'get',  '/api/cart'],
    ['cart.add',           () => cart.addToCart(1, 2),                                           'post', '/api/cart/items'],
    ['cart.update',        () => cart.updateCartItem(5, 3),                                      'put',  '/api/cart/items/5'],
    ['cart.remove',        () => cart.removeCartItem(5),                                         'delete', '/api/cart/items/5'],
    ['cart.clear',         () => cart.clearCart(),                                               'delete', '/api/cart'],
    ['orders.checkout',    () => orders.checkout(7),                                             'post', '/api/orders/checkout'],
    ['orders.list',        () => orders.getOrders(),                                             'get',  '/api/orders'],
    ['orders.get',         () => orders.getOrder(2),                                             'get',  '/api/orders/2'],
    ['orders.payment',     () => orders.getPayment(2),                                           'get',  '/api/orders/2/payment'],
    ['admin.confirmPix',   () => admin.confirmPayment(5, 'pw', '123456'),                        'post', '/api/admin/orders/5/payment/confirm'],
    ['addresses.list',     () => addresses.getAddresses(),                                       'get',  '/api/addresses'],
    ['addresses.create',   () => addresses.createAddress({ zipCode: '1', street: 'r', number: '1', neighborhood: 'n', city: 'c', state: 'SP' }), 'post', '/api/addresses'],
    ['addresses.delete',   () => addresses.deleteAddress(4),                                     'delete', '/api/addresses/4'],
    ['admin.dashboard',    () => admin.getDashboard(),                                           'get',  '/api/admin/dashboard'],
    ['admin.products',     () => admin.getAdminProducts(0, 'can'),                               'get',  '/api/admin/products'],
    ['admin.createProduct',() => admin.createProduct({ name: 'n', description: '', imageUrl: '', price: 1, stockQuantity: 1, active: true }), 'post', '/api/admin/products'],
    ['admin.updateProduct',() => admin.updateProduct(9, { name: 'n', description: '', imageUrl: '', price: 1, stockQuantity: 1, active: true }), 'put', '/api/admin/products/9'],
    ['admin.deactivate',   () => admin.deactivateProduct(9),                                     'delete', '/api/admin/products/9'],
    ['admin.orders',       () => admin.getAdminOrders(0, 'PENDING'),                             'get',  '/api/admin/orders'],
    ['admin.orderStatus',  () => admin.updateOrderStatus(1, 'SHIPPED'),                          'patch', '/api/admin/orders/1/status'],
    ['admin.users',        () => admin.getAdminUsers(0, 'ana'),                                  'get',  '/api/admin/users'],
    ['admin.updateUser',   () => admin.updateUser(2, { active: false }),                         'patch', '/api/admin/users/2'],
    ['admin.userOrders',   () => admin.getUserOrders(2),                                         'get',  '/api/admin/users/2/orders'],
  ])('%s', async (_name, call, method, url) => {
    await call();
    expect(last().method).toBe(method);
    expect(last().url).toBe(url);
  });

  it('envia parâmetros de busca e corpo corretos', async () => {
    await admin.getAdminOrders(2, 'PENDING');
    expect(last().params).toEqual({ page: 2, size: 10, status: 'PENDING' });

    await admin.getAdminProducts(0, '');
    expect(last().params.search).toBeUndefined();

    await admin.updateOrderStatus(1, 'CANCELLED');
    expect(body()).toEqual({ status: 'CANCELLED' });

    await cart.addToCart(4, 2);
    expect(body()).toEqual({ productId: 4, quantity: 2 });

    await orders.checkout(7);
    expect(body()).toEqual({ addressId: 7, paymentMethod: 'PIX' });

    await auth.login({ email: 'a@a', password: 'p', mfaCode: '123456' });
    expect(body()).toEqual({ email: 'a@a', password: 'p', mfaCode: '123456' });

    await account.deleteAccount('pw', '');
    expect(body()).toEqual({ password: 'pw' });          // código vazio não é enviado
    await account.deleteAccount('pw', '654321');
    expect(body()).toEqual({ password: 'pw', mfaCode: '654321' });

    await admin.confirmPayment(5, 'pw', '123456');
    expect(body()).toEqual({ password: 'pw', mfaCode: '123456' });   // revisão 5: step-up no corpo
    await admin.refundPayment(5, 'pw');
    expect(body()).toEqual({ password: 'pw' });

    await account.setupMfa('pw');
    expect(body()).toEqual({ password: 'pw' });
    await account.changePassword('pw', 'nova1234', '');
    expect(body()).toEqual({ currentPassword: 'pw', newPassword: 'nova1234' });   // código vazio não é enviado
    await account.changePassword('pw', 'nova1234', '123456');
    expect(body()).toEqual({ currentPassword: 'pw', newPassword: 'nova1234', mfaCode: '123456' });

    await orders.requestReturn(2);
    expect(body()).toEqual({});
    await admin.getAuditLogs(0, '');
    expect(last().params).toEqual({ page: 0, size: 20, action: undefined });
  });
});
