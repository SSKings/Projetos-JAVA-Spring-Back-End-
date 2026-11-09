import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../contexts/AuthProvider';
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
    vi.mocked(authApi.login).mockResolvedValue({ token: 't', name: 'Ana', email: 'ana@a.com', role: 'CUSTOMER', mfaEnabled: false, mfaSetupRequired: false });
    renderAt('/login', { from: { pathname: '/orders' } });

    await fill();

    expect(await screen.findByText('ORDERS PAGE')).toBeInTheDocument();
    expect(authApi.login).toHaveBeenCalledWith({ email: 'ana@a.com', password: 'secret' });
  });

  it('conta com MFA: pede o código e envia junto na segunda tentativa', async () => {
    vi.mocked(authApi.login)
      .mockRejectedValueOnce(httpError('MFA code required'))
      .mockResolvedValueOnce({ token: 't', name: 'Ana', email: 'ana@a.com', role: 'CUSTOMER', mfaEnabled: true, mfaSetupRequired: false });
    renderAt('/login');

    await fill();
    const code = await screen.findByLabelText('Código de verificação');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    await userEvent.type(code, '12ab3456');
    expect(code).toHaveValue('123456');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByText('HOME PAGE')).toBeInTheDocument();
    expect(authApi.login).toHaveBeenLastCalledWith({ email: 'ana@a.com', password: 'secret', mfaCode: '123456' });
  });

  it('bloqueio por tentativas e rate limit mostram mensagens claras', async () => {
    vi.mocked(authApi.login)
      .mockRejectedValueOnce({ response: { status: 429, data: { message: 'Too many failed attempts. Try again later' } } })
      .mockRejectedValueOnce({ response: { status: 429, data: { message: 'Too many requests' } } });
    renderAt('/login');
    await fill();
    expect(await screen.findByText(/Tente de novo em 15 minutos/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));
    expect(await screen.findByText(/Aguarde um minuto/)).toBeInTheDocument();
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

  const field = (label: string) => screen.getByLabelText(label);

  type Fill = { name?: string; email?: string; password?: string; confirm?: string; phone?: string; terms?: boolean };
  const fill = async ({ name = 'Ana Souza', email = 'Ana@A.com', password = 'abc12345', confirm, phone = '', terms = true }: Fill = {}) => {
    confirm ??= password;
    await userEvent.type(field('Nome completo'), name);
    await userEvent.type(field('Email'), email);
    await userEvent.type(field('Senha'), password);
    await userEvent.type(field('Confirmar senha'), confirm);
    if (phone) await userEvent.type(field('Telefone (opcional)'), phone);
    if (terms) await userEvent.click(screen.getByRole('checkbox', { name: /Termos de uso/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Criar Conta' }));
  };

  it('envio vazio mostra os erros de todos os campos obrigatórios sem chamar a API', async () => {
    renderAt('/register');
    await userEvent.click(screen.getByRole('button', { name: 'Criar Conta' }));

    expect(screen.getByText('Informe seu nome.')).toBeInTheDocument();
    expect(screen.getByText('Informe seu email.')).toBeInTheDocument();
    expect(screen.getByText('Informe uma senha.')).toBeInTheDocument();
    expect(screen.getByText('Confirme a senha.')).toBeInTheDocument();
    expect(field('Telefone (opcional)')).not.toHaveAttribute('aria-invalid');
    expect(authApi.register).not.toHaveBeenCalled();
  });

  it('valida senha curta sem chamar a API', async () => {
    renderAt('/register');
    await fill({ password: 'abc1234' });
    expect(screen.getByText('A senha deve ter no mínimo 8 caracteres.')).toBeInTheDocument();
    expect(field('Senha')).toHaveAttribute('aria-invalid', 'true');
    expect(authApi.register).not.toHaveBeenCalled();
  });

  it('valida nome, email, confirmação e telefone', async () => {
    renderAt('/register');
    await fill({ name: 'Ana', email: 'ana@a', confirm: 'abc999', phone: '1199' });
    expect(screen.getByText('Informe nome e sobrenome.')).toBeInTheDocument();
    expect(screen.getByText(/Informe um email válido/)).toBeInTheDocument();
    expect(screen.getByText('As senhas não conferem.')).toBeInTheDocument();
    expect(screen.getByText(/Telefone inválido/)).toBeInTheDocument();
    expect(authApi.register).not.toHaveBeenCalled();
  });

  it('sem aceitar os termos não envia e mostra o motivo; links abrem os documentos', async () => {
    renderAt('/register');
    await fill({ terms: false });
    expect(screen.getByText(/É preciso aceitar os termos/)).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /Termos de uso/ })).toHaveAttribute('aria-invalid', 'true');
    expect(authApi.register).not.toHaveBeenCalled();
    expect(screen.getByRole('link', { name: 'Termos de uso' })).toHaveAttribute('href', '/termos');
    expect(screen.getByRole('link', { name: 'Política de privacidade' })).toHaveAttribute('href', '/privacidade');

    await userEvent.click(screen.getByRole('checkbox', { name: /Termos de uso/ }));
    expect(screen.queryByText(/É preciso aceitar os termos/)).not.toBeInTheDocument();
  });

  it('erro aparece ao sair do campo, antes do envio', async () => {
    renderAt('/register');
    await userEvent.type(field('Email'), 'x');
    expect(screen.queryByText(/email válido/)).not.toBeInTheDocument();
    await userEvent.tab();
    expect(screen.getByText(/Informe um email válido/)).toBeInTheDocument();
  });

  it('cadastro com sucesso normaliza os dados, aplica máscara no telefone e vai para a home', async () => {
    vi.mocked(authApi.register).mockResolvedValue({ token: 't', name: 'Ana', email: 'ana@a.com', role: 'CUSTOMER', mfaEnabled: false, mfaSetupRequired: false });
    renderAt('/register');
    await userEvent.type(field('Telefone (opcional)'), '11987654321');
    expect(field('Telefone (opcional)')).toHaveValue('(11) 98765-4321');
    await fill({ name: ' Ana Souza ' });

    expect(await screen.findByText('HOME PAGE')).toBeInTheDocument();
    expect(authApi.register).toHaveBeenCalledWith({
      name: 'Ana Souza', email: 'ana@a.com', password: 'abc12345', phone: '(11) 98765-4321', acceptTerms: true,
    });
  });

  it('sem telefone envia phone undefined', async () => {
    vi.mocked(authApi.register).mockResolvedValue({ token: 't', name: 'Ana', email: 'ana@a.com', role: 'CUSTOMER', mfaEnabled: false, mfaSetupRequired: false });
    renderAt('/register');
    await fill();
    expect(await screen.findByText('HOME PAGE')).toBeInTheDocument();
    expect(vi.mocked(authApi.register).mock.calls[0][0].phone).toBeUndefined();
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
