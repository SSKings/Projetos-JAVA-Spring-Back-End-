import api from './axios';
import type { AuthResponse } from '../types';

export const register = (data: { name: string; email: string; password: string; phone?: string; acceptTerms: boolean }) =>
  api.post<AuthResponse>('/api/auth/register', data).then(r => r.data);

/** mfaCode só é enviado por quem ativou o MFA. */
export const login = (data: { email: string; password: string; mfaCode?: string }) =>
  api.post<AuthResponse>('/api/auth/login', data).then(r => r.data);

/** Revoga no servidor este token e todos os outros do usuário. */
export const logout = () => api.post('/api/auth/logout');
