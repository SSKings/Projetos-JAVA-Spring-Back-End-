import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider } from '../../contexts/AuthProvider';
import VerifyEmailPage from './VerifyEmailPage';
import ForgotPasswordPage from './ForgotPasswordPage';
import ResetPasswordPage from './ResetPasswordPage';
import EmailVerificationBanner from '../../components/EmailVerificationBanner';
import * as authApi from '../../api/auth';
import * as accountApi from '../../api/account';
import { loginAs } from '../../test/utils';

vi.mock('../../api/auth');
vi.mock('../../api/account');

const TOKEN = 'A'.repeat(43);
const httpError = (message: string) => ({ response: { status: 400, data: { message } } });

/** Mostra a URL atual: o token não pode ficar na barra de endereço. */
function Url() {
  const l = useLocation();
  return <span data-testid="url">{l.pathname + l.search + l.hash}</span>;
}

const renderAt = (route: string, element: React.ReactElement, path: string) => render(
  <MemoryRouter initialEntries={[route]}>
    <AuthProvider>
      <EmailVerificationBanner />
      <Routes><Route path={path} element={<>{element}<Url /></>} /></Routes>
    </AuthProvider>
  </MemoryRouter>,
);

beforeEach(() => {
  vi.resetAllMocks();
  localStorage.clear();
});

describe('Confirmar e-mail (ADR-025)', () => {
  it('confirma uma vez, tira o token da URL e some o aviso', async () => {
    loginAs('CUSTOMER', { name: 'Ana', email: 'ana@a.com' });
    localStorage.setItem('user', JSON.stringify({ name: 'Ana', email: 'ana@a.com', role: 'CUSTOMER', emailVerified: false }));
    vi.mocked(authApi.verifyEmail).mockResolvedValue({} as never);
    renderAt(`/verificar-email#token=${TOKEN}`, <VerifyEmailPage />, '/verificar-email');

    expect(await screen.findByRole('status')).toHaveTextContent('E-mail confirmado');
    expect(authApi.verifyEmail).toHaveBeenCalledTimes(1);
    expect(authApi.verifyEmail).toHaveBeenCalledWith(TOKEN);
    expect(screen.getByTestId('url')).toHaveTextContent(/^\/verificar-email$/);
    expect(screen.queryByRole('region', { name: 'Confirme seu e-mail' })).not.toBeInTheDocument();
    expect(JSON.parse(localStorage.getItem('user')!).emailVerified).toBe(true);
    expect(screen.getByRole('link', { name: 'Continuar comprando' })).toBeInTheDocument();
  });

  it('link inválido mostra o erro traduzido; sem login, oferece entrar', async () => {
    vi.mocked(authApi.verifyEmail).mockRejectedValue(httpError('Invalid or expired link'));
    renderAt(`/verificar-email#token=${TOKEN}`, <VerifyEmailPage />, '/verificar-email');
    expect(await screen.findByRole('alert')).toHaveTextContent('inválido, já foi usado ou expirou');
    expect(screen.getByRole('link', { name: 'Entrar' })).toBeInTheDocument();
  });
});

describe('Aviso de e-mail não confirmado', () => {
  it('aparece só para quem não confirmou; reenviar mostra o resultado (inclusive o intervalo mínimo)', async () => {
    localStorage.setItem('token', 'tok');
    localStorage.setItem('user', JSON.stringify({ name: 'Ana', email: 'ana@a.com', role: 'CUSTOMER', emailVerified: false }));
    vi.mocked(accountApi.resendVerification).mockResolvedValueOnce({} as never)
      .mockRejectedValueOnce(httpError('Wait a few minutes before requesting another email'));
    render(<MemoryRouter><AuthProvider><EmailVerificationBanner /></AuthProvider></MemoryRouter>);

    expect(screen.getByRole('region', { name: 'Confirme seu e-mail' })).toHaveTextContent('ana@a.com');
    await userEvent.click(screen.getByRole('button', { name: 'Reenviar e-mail' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Enviamos um novo link para ana@a.com');
    await userEvent.click(screen.getByRole('button', { name: 'Reenviar e-mail' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Aguarde alguns minutos');
  });

  it('não aparece para visitante nem para quem confirmou (sessão antiga conta como confirmada)', () => {
    const { unmount } = render(<MemoryRouter><AuthProvider><EmailVerificationBanner /></AuthProvider></MemoryRouter>);
    expect(screen.queryByRole('region')).not.toBeInTheDocument();
    unmount();
    loginAs('CUSTOMER');                                   // sem o campo emailVerified (sessão salva antes)
    render(<MemoryRouter><AuthProvider><EmailVerificationBanner /></AuthProvider></MemoryRouter>);
    expect(screen.queryByRole('region')).not.toBeInTheDocument();
  });
});

describe('Esqueci a senha', () => {
  it('mostra a mesma mensagem exista a conta ou não (envia o e-mail sem espaços)', async () => {
    vi.mocked(authApi.forgotPassword).mockResolvedValue({} as never);
    renderAt('/esqueci-senha', <ForgotPasswordPage />, '/esqueci-senha');
    await userEvent.type(screen.getByLabelText('Email da sua conta'), '  ana@a.com ');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar link' }));
    expect(authApi.forgotPassword).toHaveBeenCalledWith('ana@a.com');
    expect(await screen.findByRole('status')).toHaveTextContent('Se houver uma conta com ana@a.com');
  });

  it('erro de rede ou limite aparece', async () => {
    vi.mocked(authApi.forgotPassword).mockRejectedValue({ response: { status: 429, data: { message: 'Too many requests' } } });
    renderAt('/esqueci-senha', <ForgotPasswordPage />, '/esqueci-senha');
    await userEvent.type(screen.getByLabelText('Email da sua conta'), 'ana@a.com');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar link' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Aguarde um minuto');
  });
});

describe('Redefinir senha', () => {
  const fill = async (password: string, confirm = password) => {
    await userEvent.type(screen.getByLabelText('Nova senha'), password);
    await userEvent.type(screen.getByLabelText('Confirme a nova senha'), confirm);
    await userEvent.click(screen.getByRole('button', { name: 'Salvar nova senha' }));
  };

  it('tira o token da URL, valida antes de enviar e conclui', async () => {
    vi.mocked(authApi.resetPassword).mockResolvedValue({} as never);
    renderAt(`/redefinir-senha#token=${TOKEN}`, <ResetPasswordPage />, '/redefinir-senha');
    await waitFor(() => expect(screen.getByTestId('url')).toHaveTextContent(/^\/redefinir-senha$/));

    await fill('curta1');
    expect(screen.getByRole('alert')).toHaveTextContent('no mínimo 8');
    await userEvent.clear(screen.getByLabelText('Nova senha'));
    await userEvent.clear(screen.getByLabelText('Confirme a nova senha'));
    await fill('Libelula5732x', 'outra');
    expect(screen.getByRole('alert')).toHaveTextContent('As senhas não conferem.');
    expect(authApi.resetPassword).not.toHaveBeenCalled();

    await userEvent.clear(screen.getByLabelText('Nova senha'));
    await userEvent.clear(screen.getByLabelText('Confirme a nova senha'));
    await fill('Libelula5732x');
    expect(authApi.resetPassword).toHaveBeenCalledWith(TOKEN, 'Libelula5732x');
    expect(await screen.findByRole('status')).toHaveTextContent('encerramos as sessões abertas');
  });

  it('senha comum ou link vencido: erro traduzido; sem token: pede outro link', async () => {
    vi.mocked(authApi.resetPassword).mockRejectedValue(httpError('Password is too common. Choose a less predictable one'));
    const { unmount } = renderAt(`/redefinir-senha#token=${TOKEN}`, <ResetPasswordPage />, '/redefinir-senha');
    await fill('Senha2026x');
    expect(await screen.findByRole('alert')).toHaveTextContent('muito comum');
    unmount();
    // token na query (link antigo ou adulterado) não é aceito: só no fragmento, que não vai para o servidor
    renderAt(`/redefinir-senha?token=${TOKEN}`, <ResetPasswordPage />, '/redefinir-senha');
    expect(screen.getByRole('alert')).toHaveTextContent('Link incompleto');
    expect(screen.getByRole('link', { name: 'Peça um novo link' })).toHaveAttribute('href', '/esqueci-senha');
  });
});
