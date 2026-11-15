import api from './axios';
import type { AuthResponse, MfaSetup, Profile } from '../types';

export const getProfile = () => api.get<Profile>('/api/account').then(r => r.data);

export const updateProfile = (data: { name: string; phone?: string }) =>
  api.put<Profile>('/api/account', data).then(r => r.data);

/** JSON com todos os dados do titular (LGPD art. 18, II e V). */
export const exportData = () => api.get<unknown>('/api/account/data-export').then(r => r.data);

export const deleteAccount = (password: string, mfaCode?: string) =>
  api.post('/api/account/delete', { password, mfaCode: mfaCode || undefined });

/** Exige a senha: com só o token, quem roubasse a sessão ativava o MFA e trancava o dono fora. */
export const setupMfa = (password: string) =>
  api.post<MfaSetup>('/api/account/mfa/setup', { password }).then(r => r.data);

/** Troca a senha; o back-end encerra as outras sessões e devolve um token novo. */
export const changePassword = (currentPassword: string, newPassword: string, mfaCode?: string) =>
  api.post<AuthResponse>('/api/account/password', { currentPassword, newPassword, mfaCode: mfaCode || undefined })
    .then(r => r.data);

export const enableMfa = (code: string) => api.post<AuthResponse>('/api/account/mfa/enable', { code }).then(r => r.data);

export const disableMfa = (password: string, mfaCode: string) =>
  api.post<AuthResponse>('/api/account/mfa/disable', { password, mfaCode }).then(r => r.data);

/** Reenvia o link de confirmação do e-mail (no máximo um a cada 2 min). */
export const resendVerification = () => api.post('/api/account/verification-email');
