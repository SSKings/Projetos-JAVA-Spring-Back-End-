import { createContext, useContext, useState, type ReactNode } from 'react';
import * as authApi from '../api/auth';
import type { AuthResponse, Role } from '../types';

interface AuthUser {
  name: string;
  email: string;
  role: Role;
  mfaEnabled: boolean;
  /** Admin precisa ativar o MFA antes de entrar na área administrativa. */
  mfaSetupRequired: boolean;
}

interface AuthContextType {
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

const AuthContext = createContext<AuthContextType | null>(null);

const toUser = (resp: AuthResponse): AuthUser => ({
  name: resp.name, email: resp.email, role: resp.role,
  mfaEnabled: !!resp.mfaEnabled, mfaSetupRequired: !!resp.mfaSetupRequired,
});

function readPersistedUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem('user');
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    // Sessões salvas antes da existência de papéis/MFA são tratadas como cliente sem MFA.
    return { mfaEnabled: false, mfaSetupRequired: false, ...parsed, role: parsed.role ?? 'CUSTOMER' };
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(readPersistedUser);
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('token'));

  const saveUser = (next: AuthUser) => {
    localStorage.setItem('user', JSON.stringify(next));
    setUser(next);
  };

  const updateSession = (resp: AuthResponse) => {
    localStorage.setItem('token', resp.token);
    setToken(resp.token);
    saveUser(toUser(resp));
  };

  const login = async (email: string, password: string, mfaCode?: string) => {
    updateSession(await authApi.login({ email, password, mfaCode: mfaCode || undefined }));
  };

  const register = async (name: string, email: string, password: string, phone: string | undefined, acceptTerms: boolean) => {
    updateSession(await authApi.register({ name, email, password, phone, acceptTerms }));
  };

  const clearSession = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setToken(null);
    setUser(null);
  };

  const logout = async () => {
    try {
      await authApi.logout(); // revoga o token no servidor
    } catch {
      // sem rede ou token já inválido: a sessão local é encerrada mesmo assim
    }
    clearSession();
  };

  const updateName = (name: string) => {
    if (user) saveUser({ ...user, name });
  };

  return (
    <AuthContext.Provider value={{
      user, token, login, register, logout, clearSession, updateSession, updateName,
      isAuthenticated: !!token, isAdmin: !!token && user?.role === 'ADMIN',
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
