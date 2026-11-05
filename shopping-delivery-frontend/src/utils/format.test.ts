import { describe, expect, it } from 'vitest';
import { formatBRL, formatDate, getErrorMessage, nextOrderStatuses } from './format';

const axiosError = (data?: object) => ({ response: { status: 400, data } });

describe('formatBRL', () => {
  it('formata em reais', () => {
    expect(formatBRL(1234.5)).toMatch(/R\$\s?1\.234,50/);
  });
});

describe('formatDate', () => {
  it('formata ISO em pt-BR', () => {
    expect(formatDate('2026-03-15T12:00:00')).toMatch(/15.*mar.*2026/);
  });
});

describe('getErrorMessage', () => {
  it('sem resposta = servidor fora do ar', () => {
    expect(getErrorMessage(new Error('Network Error'))).toMatch(/conectar ao servidor/);
  });

  it('traduz mensagens conhecidas do back-end', () => {
    expect(getErrorMessage(axiosError({ message: 'Email already in use' }))).toBe('Este email já está em uso.');
    expect(getErrorMessage(axiosError({ message: 'Account is blocked' }))).toMatch(/bloqueada/);
  });

  it('junta erros de validação', () => {
    expect(getErrorMessage(axiosError({ message: 'Validation failed', errors: ['A', 'B'] }))).toBe('A B');
  });

  it('repassa mensagem desconhecida e usa fallback quando não há corpo', () => {
    expect(getErrorMessage(axiosError({ message: 'Something else' }))).toBe('Something else');
    expect(getErrorMessage(axiosError(), 'fallback')).toBe('fallback');
  });
});

describe('nextOrderStatuses', () => {
  it('espelha as transições do back-end', () => {
    expect(nextOrderStatuses.PENDING).toEqual(['CONFIRMED', 'CANCELLED']);
    expect(nextOrderStatuses.SHIPPED).toEqual(['DELIVERED']);
    expect(nextOrderStatuses.DELIVERED).toEqual([]);
    expect(nextOrderStatuses.CANCELLED).toEqual([]);
  });
});
