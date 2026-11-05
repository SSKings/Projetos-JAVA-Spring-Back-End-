import type { OrderStatus, PaymentStatus } from '../types';

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

export const paymentStatusConfig: Record<PaymentStatus, { label: string; className: string }> = {
  PENDING:        { label: 'Aguardando Pix', className: 'bg-yellow-100 text-yellow-700' },
  PAID:           { label: 'Pix pago',       className: 'bg-green-100 text-green-700' },
  EXPIRED:        { label: 'Pix expirado',   className: 'bg-gray-100 text-gray-600' },
  CANCELLED:      { label: 'Pix cancelado',  className: 'bg-gray-100 text-gray-600' },
  REFUND_PENDING: { label: 'Estorno pendente', className: 'bg-red-100 text-red-700' },
  REFUNDED:       { label: 'Pix estornado',  className: 'bg-gray-100 text-gray-600' },
};

/** Todos os status, na ordem do fluxo. As transições permitidas vêm do back-end em AdminOrder.nextStatuses. */
export const ORDER_STATUSES: OrderStatus[] = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

const backendMessages: Record<string, string> = {
  'Email already in use': 'Este email já está em uso.',
  'Invalid credentials': 'Email ou senha inválidos.',
  'Account is blocked': 'Esta conta está bloqueada. Fale com o suporte.',
  'You cannot change your own account': 'Você não pode alterar a sua própria conta.',
  'Address is linked to an order': 'Este endereço já foi usado em um pedido e não pode ser excluído.',
  'Order payment is pending': 'O pagamento Pix deste pedido ainda não foi confirmado.',
  'Payment is not pending': 'Este pagamento não está mais pendente.',
  'Payment not found': 'Pagamento não encontrado.',
  'Payment cannot be refunded': 'Este pagamento não pode ser estornado.',
  'MFA code required': 'Digite o código de 6 dígitos do seu app autenticador.',
  'Invalid MFA code': 'Código de verificação inválido ou já utilizado.',
  'MFA already enabled': 'A verificação em duas etapas já está ativa.',
  'MFA is not enabled': 'A verificação em duas etapas não está ativa.',
  'Start the MFA setup first': 'Gere o QR Code antes de confirmar o código.',
  'Invalid password': 'Senha incorreta.',
  'Too many failed attempts. Try again later': 'Muitas tentativas sem sucesso. A conta foi bloqueada temporariamente; tente mais tarde.',
  'Too many requests': 'Muitas tentativas em pouco tempo. Aguarde um minuto e tente de novo.',
  'Account has orders in progress': 'Você tem pedidos em andamento. Aguarde a entrega ou cancele-os antes de excluir a conta.',
  'Order can no longer be cancelled': 'Este pedido já foi enviado e não pode mais ser cancelado.',
  'Return period has expired or a return was already requested': 'O prazo de 7 dias terminou ou a devolução já foi solicitada.',
  'Access denied': 'Você não tem permissão para esta ação.',
  'The resource was modified concurrently. Reload and try again': 'O pedido foi alterado ao mesmo tempo por outra ação. Recarregue a página e tente de novo.',
};

/** "Cannot change order status from SHIPPED to CANCELLED" → "Não é possível mudar o pedido de Enviado para Cancelado." */
function translateTransition(message: string): string | undefined {
  const m = /^Cannot change order status from ([A-Z]+) to ([A-Z]+)$/.exec(message);
  const label = (s: string) => (Object.hasOwn(orderStatusConfig, s) ? orderStatusConfig[s as OrderStatus].label : undefined);
  const from = m ? label(m[1]) : undefined;
  const to = m ? label(m[2]) : undefined;
  return from && to ? `Não é possível mudar o pedido de ${from} para ${to}.` : undefined;
}

/** Extrai uma mensagem legível de um erro do axios. */
export function getErrorMessage(err: unknown, fallback = 'Ocorreu um erro. Tente novamente.'): string {
  const resp = (err as { response?: { data?: { message?: string; errors?: string[] } } }).response;
  if (!resp) return 'Não foi possível conectar ao servidor. Verifique se o back-end está rodando.';
  const { message, errors } = resp.data ?? {};
  if (errors?.length) return errors.join(' ');
  if (message) return backendMessages[message] ?? translateTransition(message) ?? message;
  return fallback;
}
