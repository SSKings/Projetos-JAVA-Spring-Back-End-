import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { AuthProvider, useAuth } from './AuthContext';
import * as authApi from '../api/auth';
import type { AuthResponse } from '../types';

vi.mock('../api/auth');

const wrapper = ({ children }: { children: ReactNode }) => <AuthProvider>{children}</AuthProvider>;
const resp = (over: Partial<AuthResponse> = {}): AuthResponse => ({
  token: 't1', name: 'Adm', email: 'adm@x.com', role: 'ADMIN', mfaEnabled: false, mfaSetupRequired: false, ...over,
});

describe('AuthContext', () => {
  beforeEach(() => vi.resetAllMocks());

  it('começa deslogado', () => {
    const { result } = renderHook(() => useAuth(), { wrapper });
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.isAdmin).toBe(false);
    expect(result.current.user).toBeNull();
  });

  it('login de admin persiste a sessão (com estado do MFA) e marca isAdmin', async () => {
    vi.mocked(authApi.login).mockResolvedValue(resp({ mfaSetupRequired: true }));
    const { result } = renderHook(() => useAuth(), { wrapper });

    await act(() => result.current.login('adm@x.com', 'pw'));

    expect(authApi.login).toHaveBeenCalledWith({ email: 'adm@x.com', password: 'pw', mfaCode: undefined });
    expect(result.current.isAdmin).toBe(true);
    expect(localStorage.getItem('token')).toBe('t1');
    expect(JSON.parse(localStorage.getItem('user')!)).toEqual({
      name: 'Adm', email: 'adm@x.com', role: 'ADMIN', mfaEnabled: false, mfaSetupRequired: true,
    });
  });

  it('login com código MFA repassa o código', async () => {
    vi.mocked(authApi.login).mockResolvedValue(resp({ mfaEnabled: true }));
    const { result } = renderHook(() => useAuth(), { wrapper });
    await act(() => result.current.login('adm@x.com', 'pw', '123456'));
    expect(authApi.login).toHaveBeenCalledWith({ email: 'adm@x.com', password: 'pw', mfaCode: '123456' });
    expect(result.current.user?.mfaEnabled).toBe(true);
  });

  it('cadastro envia o aceite dos termos e cria sessão de cliente', async () => {
    vi.mocked(authApi.register).mockResolvedValue(resp({ token: 't2', name: 'Ana', email: 'ana@a.com', role: 'CUSTOMER' }));
    const { result } = renderHook(() => useAuth(), { wrapper });

    await act(() => result.current.register('Ana', 'ana@a.com', 'senha123', undefined, true));

    expect(authApi.register).toHaveBeenCalledWith({ name: 'Ana', email: 'ana@a.com', password: 'senha123', phone: undefined, acceptTerms: true });
    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.isAdmin).toBe(false);
  });

  it('logout revoga no servidor e limpa estado e localStorage (mesmo se a API falhar)', async () => {
    localStorage.setItem('token', 't');
    localStorage.setItem('user', JSON.stringify({ name: 'A', email: 'a', role: 'ADMIN' }));
    vi.mocked(authApi.logout).mockRejectedValue(new Error('Network Error'));
    const { result } = renderHook(() => useAuth(), { wrapper });
    expect(result.current.isAdmin).toBe(true);

    await act(() => result.current.logout());

    expect(authApi.logout).toHaveBeenCalled();
    expect(result.current.isAuthenticated).toBe(false);
    expect(localStorage.getItem('token')).toBeNull();
  });

  it('updateSession troca o token; updateName atualiza só o nome; clearSession não chama a API', () => {
    localStorage.setItem('token', 'old');
    localStorage.setItem('user', JSON.stringify({ name: 'A', email: 'a', role: 'ADMIN', mfaSetupRequired: true }));
    const { result } = renderHook(() => useAuth(), { wrapper });

    act(() => result.current.updateSession(resp({ token: 'new', mfaEnabled: true })));
    expect(localStorage.getItem('token')).toBe('new');
    expect(result.current.user?.mfaSetupRequired).toBe(false);

    act(() => result.current.updateName('Novo Nome'));
    expect(result.current.user?.name).toBe('Novo Nome');
    expect(JSON.parse(localStorage.getItem('user')!).name).toBe('Novo Nome');

    act(() => result.current.clearSession());
    expect(result.current.isAuthenticated).toBe(false);
    expect(authApi.logout).not.toHaveBeenCalled();
    act(() => result.current.updateName('ignorado')); // sem usuário: nada acontece
    expect(result.current.user).toBeNull();
  });

  it('sessão antiga sem role/MFA é tratada como cliente sem MFA', () => {
    localStorage.setItem('token', 't');
    localStorage.setItem('user', JSON.stringify({ name: 'A', email: 'a' }));
    const { result } = renderHook(() => useAuth(), { wrapper });
    expect(result.current.user?.role).toBe('CUSTOMER');
    expect(result.current.user?.mfaSetupRequired).toBe(false);
  });

  it('JSON corrompido no localStorage não quebra o app', () => {
    localStorage.setItem('user', '{oops');
    const { result } = renderHook(() => useAuth(), { wrapper });
    expect(result.current.user).toBeNull();
  });

  it('useAuth fora do provider lança erro', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => renderHook(() => useAuth())).toThrow(/AuthProvider/);
  });
});
