import { describe, expect, it } from 'vitest';
import { ORDER_STATUSES, formatBRL, formatDate, getErrorMessage, orderStatusConfig } from './format';

const axiosError = (data?: object) => ({ response: { status: 400, data } });

describe('formatBRL', () => {
  it('formata em reais', () => {
    expect(formatBRL(1234.5)).toMatch(/R\$\s?1\.234,50/);
  });
});

describe('formatDate', () => {
  it('formata ISO em pt-BR', () => {
    expect(formatDate('2026-03-15T12:00:00')).toMatch(/15.*mar.*2026/);
  });
});

describe('getErrorMessage', () => {
  it('sem resposta = servidor fora do ar', () => {
    expect(getErrorMessage(new Error('Network Error'))).toMatch(/conectar ao servidor/);
  });

  it('traduz mensagens conhecidas do back-end', () => {
    expect(getErrorMessage(axiosError({ message: 'Email already in use' }))).toBe('Este email já está em uso.');
    expect(getErrorMessage(axiosError({ message: 'Account is blocked' }))).toMatch(/bloqueada/);
    expect(getErrorMessage(axiosError({ message: 'Address is linked to an order' }))).toMatch(/usado em um pedido/);
    expect(getErrorMessage(axiosError({ message: 'Order payment is pending' }))).toMatch(/Pix deste pedido ainda não foi confirmado/);
    // respostas novas da revisão de segurança 2 (413, 404 de rota, sort inválido)
    expect(getErrorMessage(axiosError({ message: 'Request body too large' }))).toBe('Os dados enviados são grandes demais.');
    expect(getErrorMessage(axiosError({ message: 'Resource not found' }))).toBe('Recurso não encontrado.');
    expect(getErrorMessage(axiosError({ message: 'Invalid sort property' }))).toBe('Ordenação inválida.');
  });

  it('traduz transição de status recusada pelo back-end', () => {
    expect(getErrorMessage(axiosError({ message: 'Cannot change order status from SHIPPED to CANCELLED' })))
      .toBe('Não é possível mudar o pedido de Enviado para Cancelado.');
    // status desconhecido: mantém a mensagem original
    expect(getErrorMessage(axiosError({ message: 'Cannot change order status from FOO to BAR' })))
      .toBe('Cannot change order status from FOO to BAR');
  });

  it('junta erros de validação', () => {
    expect(getErrorMessage(axiosError({ message: 'Validation failed', errors: ['A', 'B'] }))).toBe('A B');
  });

  it('repassa mensagem desconhecida e usa fallback quando não há corpo', () => {
    expect(getErrorMessage(axiosError({ message: 'Something else' }))).toBe('Something else');
    expect(getErrorMessage(axiosError(), 'fallback')).toBe('fallback');
  });
});

describe('ORDER_STATUSES', () => {
  it('tem todos os status na ordem do fluxo, cada um com rótulo', () => {
    expect(ORDER_STATUSES).toEqual(['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED']);
    expect(ORDER_STATUSES.map(s => orderStatusConfig[s].label)).toEqual(['Pendente', 'Confirmado', 'Enviado', 'Entregue', 'Cancelado']);
  });
});

describe('mensagens da revisão 4', () => {
  const err = (message: string) => ({ response: { status: 400, data: { message } } });
  it.each([
    ['Maximum quantity for this product right now: 5 (Relógio)', 'Por enquanto, cada pedido pode levar no máximo 5 unidade(s) de Relógio.'],
    ['Maximum of 10 units per product across your orders awaiting payment: Caneca', 'Limite de 10 unidades de Caneca somando seus pedidos que aguardam pagamento.'],
    ['Image host not allowed. Allowed: images.unsplash.com', 'Use uma imagem hospedada em: images.unsplash.com.'],
    ['Password confirmation required', 'Confirme sua senha para esta ação.'],
    ['Too many unpaid orders in the last 24 hours. Try again later', 'Você deixou vários pedidos sem pagar nas últimas 24 horas. Tente de novo mais tarde.'],
    ['Too many accounts created from this network today. Try again later', 'Muitas contas foram criadas a partir desta rede hoje. Tente de novo amanhã.'],
    ['Password is too common. Choose a less predictable one', 'Essa senha é muito comum e fácil de adivinhar. Escolha outra (evite palavras como "senha" ou "admin" com números).'],
    ['Password must not contain your name, your email or the store name', 'A senha não pode conter seu nome, seu email ou o nome da loja.'],
    ['The last active admin cannot delete the account. Promote another admin first', 'Você é o último administrador ativo: promova outro admin antes de excluir sua conta.'],
    ['Confirm your email before placing an order', 'Confirme seu e-mail antes de fechar o pedido: abra o link que enviamos (ou peça outro no aviso no topo da página).'],
    ['Invalid or expired link', 'Este link é inválido, já foi usado ou expirou. Peça um novo.'],
    ['Email already verified', 'Seu e-mail já está confirmado.'],
    ['Mensagem desconhecida', 'Mensagem desconhecida'],
  ])('%s', (message, expected) => {
    expect(getErrorMessage(err(message))).toBe(expected);
  });
});
