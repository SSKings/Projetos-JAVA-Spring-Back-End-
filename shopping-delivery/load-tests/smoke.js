// Teste funcional da API: percorre o fluxo inteiro de compra com 1 usuário e confere cada resposta.
// Rápido (segundos) e sem efeito colateral relevante: o pedido criado é cancelado, devolvendo o estoque.
//   ./load-tests/run.sh smoke
import http from 'k6/http';
import { check, group } from 'k6';
import { BASE, anon, authed, json, registerUsers } from './lib/common.js';

export const options = {
  vus: 1,
  iterations: 1,
  // qualquer verificação que falhar reprova o teste (código de saída ≠ 0, usado no CI)
  thresholds: { checks: ['rate==1'] },
};

export default function () {
  group('saúde e catálogo (público)', () => {
    const health = http.get(`${BASE}/actuator/health`, anon('GET /actuator/health'));
    check(health, { 'health → 200 UP': (r) => r.status === 200 && r.json('status') === 'UP' });
    check(http.get(`${BASE}/actuator/prometheus`, anon('GET /actuator/prometheus')),
      { 'métricas exigem login → 401': (r) => r.status === 401 });

    const list = http.get(`${BASE}/api/products?page=0&size=5`, anon('GET /api/products'));
    check(list, { 'lista de produtos → 200 com itens': (r) => r.status === 200 && r.json('content').length > 0 });
    const search = http.get(`${BASE}/api/products?search=camiseta`, anon('GET /api/products'));
    check(search, { 'busca por nome → 200': (r) => r.status === 200 });
    check(http.get(`${BASE}/api/products/1`, anon('GET /api/products/{id}')),
      { 'detalhe do produto → 200': (r) => r.status === 200 && r.json('id') === 1 });
    check(http.get(`${BASE}/api/products/999999`, anon('GET /api/products/{id}')),
      { 'produto inexistente → 404': (r) => r.status === 404 });
  });

  const [user] = registerUsers(1, 'smoke');

  group('autenticação', () => {
    const ok = http.post(`${BASE}/api/auth/login`, json({ email: user.email, password: 'Carga12345' }), anon('POST /api/auth/login'));
    check(ok, { 'login → 200 com token': (r) => r.status === 200 && !!r.json('token') });
    const wrong = http.post(`${BASE}/api/auth/login`, json({ email: user.email, password: 'SenhaErrada1' }), anon('POST /api/auth/login'));
    check(wrong, { 'senha errada → 400': (r) => r.status === 400 });
    check(http.get(`${BASE}/api/cart`, anon('GET /api/cart')), { 'carrinho sem login → 401': (r) => r.status === 401 });
    check(http.get(`${BASE}/api/admin/orders`, authed(user.token, 'GET /api/admin/orders')),
      { 'admin para cliente → 403': (r) => r.status === 403 });
  });

  let orderId;
  group('carrinho, endereço e checkout Pix', () => {
    const add = http.post(`${BASE}/api/cart/items`, json({ productId: 1, quantity: 1 }), authed(user.token, 'POST /api/cart/items'));
    check(add, { 'adicionar ao carrinho → 200': (r) => r.status === 200 });
    const itemId = add.json('items.0.id');
    const upd = http.put(`${BASE}/api/cart/items/${itemId}`, json({ quantity: 2 }), authed(user.token, 'PUT /api/cart/items/{itemId}'));
    check(upd, { 'alterar quantidade → 200': (r) => r.status === 200 && r.json('items.0.quantity') === 2 });
    const cart = http.get(`${BASE}/api/cart`, authed(user.token, 'GET /api/cart'));
    // itemCount = linhas do carrinho (produtos distintos), não unidades — é o "N itens" do front
    check(cart, {
      'carrinho → 1 linha com 2 unidades': (r) => r.status === 200 && r.json('itemCount') === 1 && r.json('items.0.quantity') === 2,
      'total = 2 × preço unitário': (r) => Math.abs(r.json('total') - 2 * r.json('items.0.unitPrice')) < 0.001,
    });

    const addr = http.post(`${BASE}/api/addresses`, json({
      zipCode: '01000-000', street: 'Rua de Teste', number: '10', neighborhood: 'Centro', city: 'São Paulo', state: 'SP',
    }), authed(user.token, 'POST /api/addresses'));
    check(addr, { 'cadastrar endereço → 201': (r) => r.status === 201 });

    const order = http.post(`${BASE}/api/orders/checkout`, json({ addressId: addr.json('id'), paymentMethod: 'PIX' }),
      authed(user.token, 'POST /api/orders/checkout'));
    check(order, { 'checkout → 201 PENDING': (r) => r.status === 201 && r.json('status') === 'PENDING' });
    orderId = order.json('id');

    const pay = http.get(`${BASE}/api/orders/${orderId}/payment`, authed(user.token, 'GET /api/orders/{id}/payment'));
    check(pay, { 'cobrança Pix com copia e cola': (r) => r.status === 200 && String(r.json('pixCopyPaste')).startsWith('000201') });
    check(http.get(`${BASE}/api/cart`, authed(user.token, 'GET /api/cart')),
      { 'carrinho esvaziado após checkout': (r) => r.json('itemCount') === 0 });
    check(http.post(`${BASE}/api/orders/checkout`, json({ addressId: addr.json('id'), paymentMethod: 'PIX' }), authed(user.token, 'POST /api/orders/checkout')),
      { 'checkout com carrinho vazio → 400': (r) => r.status === 400 });
  });

  group('pedidos e sessão', () => {
    const orders = http.get(`${BASE}/api/orders`, authed(user.token, 'GET /api/orders'));
    check(orders, { 'meus pedidos contém o pedido': (r) => r.status === 200 && r.json().some((o) => o.id === orderId) });
    const cancel = http.post(`${BASE}/api/orders/${orderId}/cancel`, null, authed(user.token, 'POST /api/orders/{id}/cancel'));
    check(cancel, { 'cancelar pedido → CANCELLED': (r) => r.status === 200 && r.json('status') === 'CANCELLED' });
    // webhook forjado fica em alerts.js: aqui dispararia o alerta "Webhook Pix recusado" a cada execução
    check(http.post(`${BASE}/api/auth/logout`, null, authed(user.token, 'POST /api/auth/logout')), { 'logout → 204': (r) => r.status === 204 });
    check(http.get(`${BASE}/api/cart`, authed(user.token, 'GET /api/cart')), { 'token revogado após logout → 401': (r) => r.status === 401 });
  });
}
