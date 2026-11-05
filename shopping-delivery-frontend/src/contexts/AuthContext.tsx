import { createContext, useContext, useState, type ReactNode } from 'react';
import * as authApi from '../api/auth';
import type { AuthResponse, Role } from '../types';

interface AuthUser {
  name: string;
  email: string;
  role: Role;
}

interface AuthContextType {
  user: AuthUser | null;
  token: string | null;
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string, phone?: string) => Promise<void>;
  logout: () => void;
  isAuthenticated: boolean;
  isAdmin: boolean;
}

const AuthContext = createContext<AuthContextType | null>(null);

function persistAuth(resp: AuthResponse) {
  localStorage.setItem('token', resp.token);
  localStorage.setItem('user', JSON.stringify({ name: resp.name, email: resp.email, role: resp.role }));
}

function readPersistedUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem('user');
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    // Sessões salvas antes da existência de papéis são tratadas como cliente.
    return { ...parsed, role: parsed.role ?? 'CUSTOMER' };
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(readPersistedUser);
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('token'));

  const handleAuth = (resp: AuthResponse) => {
    persistAuth(resp);
    setToken(resp.token);
    setUser({ name: resp.name, email: resp.email, role: resp.role });
  };

  const login = async (email: string, password: string) => {
    const resp = await authApi.login({ email, password });
    handleAuth(resp);
  };

  const register = async (name: string, email: string, password: string, phone?: string) => {
    const resp = await authApi.register({ name, email, password, phone });
    handleAuth(resp);
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, token, login, register, logout, isAuthenticated: !!token, isAdmin: !!token && user?.role === 'ADMIN' }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
