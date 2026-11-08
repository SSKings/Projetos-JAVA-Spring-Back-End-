import { BrowserRouter, Navigate, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthProvider';
import { CartProvider } from './contexts/CartProvider';
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';
import ProductListPage from './pages/ProductListPage';
import ProductDetailPage from './pages/ProductDetailPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import CartPage from './pages/CartPage';
import CheckoutPage from './pages/checkout/CheckoutPage';
import AccountLayout from './pages/account/AccountLayout';
import OrdersPage from './pages/account/OrdersPage';
import OrderDetailPage from './pages/account/OrderDetailPage';
import PurchaseHistoryPage from './pages/account/PurchaseHistoryPage';
import AddressesPage from './pages/account/AddressesPage';
import PrivacyPage from './pages/account/PrivacyPage';
import SecurityPage from './pages/account/SecurityPage';
import AdminAuditPage from './pages/admin/AdminAuditPage';
import Footer from './components/Footer';
import { PrivacyPolicyPage, ReturnsPolicyPage, TermsPage } from './pages/legal/LegalPages';
import AdminRoute from './components/AdminRoute';
import AdminLayout from './pages/admin/AdminLayout';
import AdminDashboardPage from './pages/admin/AdminDashboardPage';
import AdminProductsPage from './pages/admin/AdminProductsPage';
import AdminOrdersPage from './pages/admin/AdminOrdersPage';
import AdminUsersPage from './pages/admin/AdminUsersPage';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <CartProvider>
          <div className="min-h-screen bg-gray-50">
            <Navbar />
            <Routes>
              {/* Public */}
              <Route path="/" element={<ProductListPage />} />
              <Route path="/products/:id" element={<ProductDetailPage />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route path="/privacidade" element={<PrivacyPolicyPage />} />
              <Route path="/termos" element={<TermsPage />} />
              <Route path="/trocas-e-devolucoes" element={<ReturnsPolicyPage />} />

              {/* Protected */}
              <Route path="/cart" element={<ProtectedRoute><CartPage /></ProtectedRoute>} />
              <Route path="/checkout" element={<ProtectedRoute><CheckoutPage /></ProtectedRoute>} />

              {/* Minha conta (layout com abas, sem segmento próprio na URL) */}
              <Route element={<ProtectedRoute><AccountLayout /></ProtectedRoute>}>
                <Route path="/orders" element={<OrdersPage />} />
                <Route path="/orders/:id" element={<OrderDetailPage />} />
                <Route path="/purchases" element={<PurchaseHistoryPage />} />
                <Route path="/addresses" element={<AddressesPage />} />
                <Route path="/privacy" element={<PrivacyPage />} />
                <Route path="/security" element={<SecurityPage />} />
              </Route>
              <Route path="/account" element={<Navigate to="/orders" replace />} />

              {/* Admin */}
              <Route path="/admin" element={<AdminRoute><AdminLayout /></AdminRoute>}>
                <Route index element={<AdminDashboardPage />} />
                <Route path="products" element={<AdminProductsPage />} />
                <Route path="orders" element={<AdminOrdersPage />} />
                <Route path="users" element={<AdminUsersPage />} />
                <Route path="audit" element={<AdminAuditPage />} />
              </Route>
            </Routes>
            <Footer />
          </div>
        </CartProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
