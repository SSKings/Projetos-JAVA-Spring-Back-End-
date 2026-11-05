import api from './axios';
import type { AuthResponse, MfaSetup, Profile } from '../types';

export const getProfile = () => api.get<Profile>('/api/account').then(r => r.data);

export const updateProfile = (data: { name: string; phone?: string }) =>
  api.put<Profile>('/api/account', data).then(r => r.data);

/** JSON com todos os dados do titular (LGPD art. 18, II e V). */
export const exportData = () => api.get<unknown>('/api/account/data-export').then(r => r.data);

export const deleteAccount = (password: string, mfaCode?: string) =>
  api.post('/api/account/delete', { password, mfaCode: mfaCode || undefined });

export const setupMfa = () => api.post<MfaSetup>('/api/account/mfa/setup').then(r => r.data);

export const enableMfa = (code: string) => api.post<AuthResponse>('/api/account/mfa/enable', { code }).then(r => r.data);

export const disableMfa = (password: string, mfaCode: string) =>
  api.post<AuthResponse>('/api/account/mfa/disable', { password, mfaCode }).then(r => r.data);
