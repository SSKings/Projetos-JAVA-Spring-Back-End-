export type Role = 'CUSTOMER' | 'ADMIN';

export interface AuthResponse {
  token: string;
  name: string;
  email: string;
  role: Role;
  mfaEnabled: boolean;
  /** Admin que precisa ativar o MFA antes de usar /api/admin/** (403 até lá). */
  mfaSetupRequired: boolean;
}

export interface Profile {
  id: number;
  name: string;
  email: string;
  phone: string | null;
  role: Role;
  createdAt: string;
  mfaEnabled: boolean;
  termsVersion: string | null;
  termsAcceptedAt: string | null;
}

export interface MfaSetup {
  secret: string;
  otpauthUri: string;
}

export interface AuditLog {
  id: number;
  createdAt: string;
  actorId: number | null;
  actorEmail: string | null;
  action: string;
  targetType: string | null;
  targetId: string | null;
  details: string | null;
  ip: string | null;
}

export interface Product {
  id: number;
  name: string;
  description: string;
  imageUrl: string;
  price: number;
  stockQuantity: number;
}

export interface Address {
  id: number;
  zipCode: string;
  street: string;
  number: string;
  complement?: string;
  neighborhood: string;
  city: string;
  state: string;
}

export interface CartItem {
  id: number;
  productId: number;
  productName: string;
  productImageUrl: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
}

export interface CartSummary {
  items: CartItem[];
  total: number;
  itemCount: number;
}

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

export interface OrderItem {
  id: number;
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export type PaymentMethod = 'PIX';
export type PaymentStatus = 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELLED' | 'REFUND_PENDING' | 'REFUNDED';

/** Espelha PaymentResponse do back-end. */
export interface Payment {
  method: PaymentMethod;
  status: PaymentStatus;
  amount: number;
  txid: string;
  /** BR Code "Pix copia e cola" — só vem enquanto o pagamento está pendente. */
  pixCopyPaste: string | null;
  createdAt: string;
  expiresAt: string;
  paidAt: string | null;
  /** Calculado no servidor; o contador não depende do relógio do navegador. */
  expiresInSeconds: number;
}

export interface Order {
  id: number;
  status: OrderStatus;
  total: number;
  createdAt: string;
  address: Address;
  items: OrderItem[];
  /** null em pedidos anteriores ao pagamento Pix. */
  payment?: Payment | null;
  deliveredAt?: string | null;
  returnRequestedAt?: string | null;
  /** Fim do prazo de arrependimento (7 dias da entrega, CDC art. 49). */
  returnDeadline?: string | null;
  canCancel?: boolean;
  canRequestReturn?: boolean;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// ---------- Admin ----------
export interface AdminProduct extends Product {
  active: boolean;
}

export interface ProductInput {
  name: string;
  description: string;
  imageUrl: string;
  price: number;
  stockQuantity: number;
  active: boolean;
}

export interface AdminOrder extends Order {
  customerId: number;
  customerName: string;
  customerEmail: string;
  /** Status para os quais o pedido pode ir (OrderStatus.nextStatuses no back-end). */
  nextStatuses: OrderStatus[];
  returnReason?: string | null;
}

export interface AdminUser {
  id: number;
  name: string;
  email: string;
  phone?: string;
  role: Role;
  active: boolean;
  createdAt: string;
  orderCount: number;
}

export interface DashboardStats {
  totalProducts: number;
  activeProducts: number;
  lowStockProducts: number;
  totalCustomers: number;
  totalOrders: number;
  pendingOrders: number;
  revenue: number;
}
