import type { OrderStatus } from '../types';

export const formatBRL = (value: number) =>
  value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

export const formatDate = (iso: string) =>
  new Date(iso).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' });

export const orderStatusConfig: Record<OrderStatus, { label: string; className: string }> = {
  PENDING:   { label: 'Pendente',   className: 'bg-yellow-100 text-yellow-700' },
  CONFIRMED: { label: 'Confirmado', className: 'bg-blue-100 text-blue-700' },
  SHIPPED:   { label: 'Enviado',    className: 'bg-purple-100 text-purple-700' },
  DELIVERED: { label: 'Entregue',   className: 'bg-green-100 text-green-700' },
  CANCELLED: { label: 'Cancelado',  className: 'bg-red-100 text-red-700' },
};

/** Espelha OrderStatus.canTransitionTo do back-end. */
export const nextOrderStatuses: Record<OrderStatus, OrderStatus[]> = {
  PENDING:   ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['SHIPPED', 'CANCELLED'],
  SHIPPED:   ['DELIVERED'],
  DELIVERED: [],
  CANCELLED: [],
};

const backendMessages: Record<string, string> = {
  'Email already in use': 'Este email já está em uso.',
  'Invalid credentials': 'Email ou senha inválidos.',
  'Account is blocked': 'Esta conta está bloqueada. Fale com o suporte.',
  'You cannot change your own account': 'Você não pode alterar a sua própria conta.',
};

/** Extrai uma mensagem legível de um erro do axios. */
export function getErrorMessage(err: unknown, fallback = 'Ocorreu um erro. Tente novamente.'): string {
  const resp = (err as { response?: { data?: { message?: string; errors?: string[] } } }).response;
  if (!resp) return 'Não foi possível conectar ao servidor. Verifique se o back-end está rodando.';
  const { message, errors } = resp.data ?? {};
  if (errors?.length) return errors.join(' ');
  if (message) return backendMessages[message] ?? message;
  return fallback;
}
