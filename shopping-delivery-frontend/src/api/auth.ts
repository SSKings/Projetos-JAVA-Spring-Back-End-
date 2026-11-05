import api from './axios';
import type { AuthResponse } from '../types';

export const register = (data: { name: string; email: string; password: string; phone?: string }) =>
  api.post<AuthResponse>('/api/auth/register', data).then(r => r.data);

export const login = (data: { email: string; password: string }) =>
  api.post<AuthResponse>('/api/auth/login', data).then(r => r.data);
