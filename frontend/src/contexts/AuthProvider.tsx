import { useState, type ReactNode } from 'react';
import * as authApi from '../api/auth';
import type { AuthResponse } from '../types';
import { AuthContext, type AuthUser } from './AuthContext';

const toUser = (resp: AuthResponse): AuthUser => ({
  name: resp.name, email: resp.email, role: resp.role,
  mfaEnabled: !!resp.mfaEnabled, mfaSetupRequired: !!resp.mfaSetupRequired, emailVerified: resp.emailVerified !== false,
});

function readPersistedUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem('user');
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    // Sessões salvas antes da existência de papéis/MFA são tratadas como cliente sem MFA.
    return { mfaEnabled: false, mfaSetupRequired: false, emailVerified: true, ...parsed, role: parsed.role ?? 'CUSTOMER' };
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

  const markEmailVerified = () => {
    if (user && !user.emailVerified) saveUser({ ...user, emailVerified: true });
  };

  return (
    <AuthContext.Provider value={{
      user, token, login, register, logout, clearSession, updateSession, updateName, markEmailVerified,
      isAuthenticated: !!token, isAdmin: !!token && user?.role === 'ADMIN',
    }}>
      {children}
    </AuthContext.Provider>
  );
}
