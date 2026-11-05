import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../contexts/AuthContext';

/** Apenas UX: o back-end exige ROLE_ADMIN (e MFA ativo, em produção) em /api/admin/**. */
export default function AdminRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isAdmin, user } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) return <Navigate to="/login" state={{ from: location }} replace />;
  if (!isAdmin) return <Navigate to="/" replace />;
  if (user?.mfaSetupRequired) return <Navigate to="/security" state={{ reason: 'admin-mfa' }} replace />;
  return <>{children}</>;
}
