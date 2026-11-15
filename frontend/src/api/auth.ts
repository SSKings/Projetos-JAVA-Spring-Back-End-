import api from './axios';
import type { AuthResponse } from '../types';

export const register = (data: { name: string; email: string; password: string; phone?: string; acceptTerms: boolean }) =>
  api.post<AuthResponse>('/api/auth/register', data).then(r => r.data);

/** mfaCode só é enviado por quem ativou o MFA. */
export const login = (data: { email: string; password: string; mfaCode?: string }) =>
  api.post<AuthResponse>('/api/auth/login', data).then(r => r.data);

/** Revoga no servidor este token e todos os outros do usuário. */
export const logout = () => api.post('/api/auth/logout');

/** Confirma o e-mail com o token do link (ADR-025). */
export const verifyEmail = (token: string) => api.post('/api/auth/verify-email', { token });

/** Sempre 202, exista a conta ou não: a tela mostra a mesma mensagem nos dois casos. */
export const forgotPassword = (email: string) => api.post('/api/auth/forgot-password', { email });

export const resetPassword = (token: string, newPassword: string) =>
  api.post('/api/auth/reset-password', { token, newPassword });
