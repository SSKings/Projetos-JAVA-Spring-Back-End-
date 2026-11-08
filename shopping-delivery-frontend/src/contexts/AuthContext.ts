import { createContext, useContext } from 'react';
import type { AuthResponse, Role } from '../types';

// Contexto e hook ficam aqui; o componente AuthProvider fica em AuthProvider.tsx. Arquivo de componente só exporta
// componentes: assim o Fast Refresh do Vite atualiza o Provider sem recarregar a página (regra only-export-components).

export interface AuthUser {
  name: string;
  email: string;
  role: Role;
  mfaEnabled: boolean;
  /** Admin precisa ativar o MFA antes de entrar na área administrativa. */
  mfaSetupRequired: boolean;
}

export interface AuthContextType {
  user: AuthUser | null;
  token: string | null;
  login: (email: string, password: string, mfaCode?: string) => Promise<void>;
  register: (name: string, email: string, password: string, phone: string | undefined, acceptTerms: boolean) => Promise<void>;
  logout: () => Promise<void>;
  /** Encerra só a sessão local (o servidor já revogou o token, ex.: conta excluída). */
  clearSession: () => void;
  /** Troca a sessão por uma nova (ex.: o back-end revogou a anterior ao ativar o MFA). */
  updateSession: (resp: AuthResponse) => void;
  updateName: (name: string) => void;
  isAuthenticated: boolean;
  isAdmin: boolean;
}

export const AuthContext = createContext<AuthContextType | null>(null);

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
