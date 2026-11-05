export type Role = 'CUSTOMER' | 'ADMIN';

export interface AuthResponse {
  token: string;
  name: string;
  email: string;
  role: Role;
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
  productName: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export interface Order {
  id: number;
  status: OrderStatus;
  total: number;
  createdAt: string;
  address: Address;
  items: OrderItem[];
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
