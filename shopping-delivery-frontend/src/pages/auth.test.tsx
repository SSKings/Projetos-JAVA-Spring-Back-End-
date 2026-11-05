import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../contexts/AuthContext';
import LoginPage from './LoginPage';
import RegisterPage from './RegisterPage';
import * as authApi from '../api/auth';

vi.mock('../api/auth');

const httpError = (message: string, errors?: string[]) => ({ response: { status: 400, data: { message, errors } } });

function renderAt(path: string, state?: unknown) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: path, state }]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/" element={<div>HOME PAGE</div>} />
          <Route path="/orders" element={<div>ORDERS PAGE</div>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => vi.resetAllMocks());

  const fill = async () => {
    await userEvent.type(screen.getByPlaceholderText('seu@email.com'), 'ana@a.com');
    await userEvent.type(screen.getByPlaceholderText('••••••'), 'secret');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));
  };

  it('login com sucesso volta para a página de origem', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ token: 't', name: 'Ana', email: 'ana@a.com', role: 'CUSTOMER' });
    renderAt('/login', { from: { pathname: '/orders' } });

    await fill();

    expect(await screen.findByText('ORDERS PAGE')).toBeInTheDocument();
    expect(authApi.login).toHaveBeenCalledWith({ email: 'ana@a.com', password: 'secret' });
  });

  it('mostra mensagem de conta bloqueada', async () => {
    vi.mocked(authApi.login).mockRejectedValue(httpError('Account is blocked'));
    renderAt('/login');

    await fill();

    expect(await screen.findByText(/conta está bloqueada/)).toBeInTheDocument();
  });

  it('mostra mensagem de credenciais inválidas', async () => {
    vi.mocked(authApi.login).mockRejectedValue(httpError('Invalid credentials'));
    renderAt('/login');

    await fill();

    expect(await screen.findByText('Email ou senha inválidos.')).toBeInTheDocument();
  });
});

describe('RegisterPage', () => {
  beforeEach(() => vi.resetAllMocks());

  const fill = async (password = '123456') => {
    await userEvent.type(screen.getByPlaceholderText('João Silva'), 'Ana');
    await userEvent.type(screen.getByPlaceholderText('seu@email.com'), 'ana@a.com');
    await userEvent.type(screen.getByPlaceholderText('Mínimo 6 caracteres'), password);
    await userEvent.click(screen.getByRole('button', { name: 'Criar Conta' }));
  };

  it('valida senha curta sem chamar a API', async () => {
    renderAt('/register');
    await fill('123');
    expect(screen.getByText(/no mínimo 6 caracteres/)).toBeInTheDocument();
    expect(authApi.register).not.toHaveBeenCalled();
  });

  it('cadastro com sucesso vai para a home', async () => {
    vi.mocked(authApi.register).mockResolvedValue({ token: 't', name: 'Ana', email: 'ana@a.com', role: 'CUSTOMER' });
    renderAt('/register');
    await fill();
    expect(await screen.findByText('HOME PAGE')).toBeInTheDocument();
  });

  it('email duplicado mostra a mensagem certa', async () => {
    vi.mocked(authApi.register).mockRejectedValue(httpError('Email already in use'));
    renderAt('/register');
    await fill();
    expect(await screen.findByText('Este email já está em uso.')).toBeInTheDocument();
  });

  it('back-end fora do ar NÃO é confundido com email duplicado (regressão do bug de CORS)', async () => {
    vi.mocked(authApi.register).mockRejectedValue(new Error('Network Error'));
    renderAt('/register');
    await fill();
    expect(await screen.findByText(/Não foi possível conectar ao servidor/)).toBeInTheDocument();
    expect(screen.queryByText(/email já está em uso/)).not.toBeInTheDocument();
  });
});
