import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { AuthProvider, useAuth } from './AuthContext';
import * as authApi from '../api/auth';

vi.mock('../api/auth');

const wrapper = ({ children }: { children: ReactNode }) => <AuthProvider>{children}</AuthProvider>;

describe('AuthContext', () => {
  beforeEach(() => vi.resetAllMocks());

  it('começa deslogado', () => {
    const { result } = renderHook(() => useAuth(), { wrapper });
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.isAdmin).toBe(false);
    expect(result.current.user).toBeNull();
  });

  it('login de admin persiste a sessão e marca isAdmin', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ token: 't1', name: 'Adm', email: 'adm@x.com', role: 'ADMIN' });
    const { result } = renderHook(() => useAuth(), { wrapper });

    await act(() => result.current.login('adm@x.com', 'pw'));

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.isAdmin).toBe(true);
    expect(localStorage.getItem('token')).toBe('t1');
    expect(JSON.parse(localStorage.getItem('user')!)).toEqual({ name: 'Adm', email: 'adm@x.com', role: 'ADMIN' });
  });

  it('cadastro cria sessão de cliente', async () => {
    vi.mocked(authApi.register).mockResolvedValue({ token: 't2', name: 'Ana', email: 'ana@a.com', role: 'CUSTOMER' });
    const { result } = renderHook(() => useAuth(), { wrapper });

    await act(() => result.current.register('Ana', 'ana@a.com', '123456'));

    expect(authApi.register).toHaveBeenCalledWith({ name: 'Ana', email: 'ana@a.com', password: '123456', phone: undefined });
    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.isAdmin).toBe(false);
  });

  it('logout limpa estado e localStorage', async () => {
    localStorage.setItem('token', 't');
    localStorage.setItem('user', JSON.stringify({ name: 'A', email: 'a', role: 'ADMIN' }));
    const { result } = renderHook(() => useAuth(), { wrapper });
    expect(result.current.isAdmin).toBe(true);

    act(() => result.current.logout());

    expect(result.current.isAuthenticated).toBe(false);
    expect(localStorage.getItem('token')).toBeNull();
  });

  it('sessão antiga sem role é tratada como cliente', () => {
    localStorage.setItem('token', 't');
    localStorage.setItem('user', JSON.stringify({ name: 'A', email: 'a' }));
    const { result } = renderHook(() => useAuth(), { wrapper });
    expect(result.current.user?.role).toBe('CUSTOMER');
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
