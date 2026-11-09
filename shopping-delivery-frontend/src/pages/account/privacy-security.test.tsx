import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PrivacyPage from './PrivacyPage';
import SecurityPage from './SecurityPage';
import * as accountApi from '../../api/account';
import { loginAs, renderWithProviders } from '../../test/utils';
import type { AuthResponse, Profile } from '../../types';

vi.mock('../../api/account');

const profile = (over: Partial<Profile> = {}): Profile => ({
  id: 2, name: 'Ana Souza', email: 'ana@a.com', phone: '(11) 98765-4321', role: 'CUSTOMER',
  createdAt: '2026-01-10T10:00:00', mfaEnabled: false, termsVersion: '2026-11-05', termsAcceptedAt: '2026-01-10T10:00:00', ...over,
});
const auth = (over: Partial<AuthResponse> = {}): AuthResponse => ({
  token: 'new-token', name: 'Ana Souza', email: 'ana@a.com', role: 'CUSTOMER', mfaEnabled: true, mfaSetupRequired: false, ...over,
});
const httpError = (message: string) => ({ response: { status: 400, data: { message } } });

beforeEach(() => {
  vi.resetAllMocks();
  loginAs('CUSTOMER', { name: 'Ana Souza', email: 'ana@a.com' });
});
afterEach(() => vi.unstubAllGlobals());

describe('PrivacyPage (LGPD)', () => {
  it('mostra e corrige os dados; nome vazio não é enviado', async () => {
    vi.mocked(accountApi.getProfile).mockResolvedValue(profile());
    vi.mocked(accountApi.updateProfile).mockResolvedValue(profile({ name: 'Ana Lima', phone: null }));
    renderWithProviders(<PrivacyPage />);

    const name = await screen.findByDisplayValue('Ana Souza');
    expect(screen.getByText(/termos aceitos em/)).toHaveTextContent('versão 2026-11-05');

    await userEvent.clear(name);
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Informe seu nome.');
    expect(accountApi.updateProfile).not.toHaveBeenCalled();

    await userEvent.type(name, 'Ana Lima');
    await userEvent.clear(screen.getByLabelText('Telefone'));
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    expect(accountApi.updateProfile).toHaveBeenCalledWith({ name: 'Ana Lima', phone: undefined });
    expect(await screen.findByRole('status')).toHaveTextContent('Dados atualizados.');
    expect(JSON.parse(localStorage.getItem('user')!).name).toBe('Ana Lima');
  });

  it('baixa os dados em JSON', async () => {
    vi.mocked(accountApi.getProfile).mockResolvedValue(profile());
    vi.mocked(accountApi.exportData).mockResolvedValue({ profile: { email: 'ana@a.com' } });
    const createObjectURL = vi.fn().mockReturnValue('blob:x');
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    renderWithProviders(<PrivacyPage />);

    await userEvent.click(await screen.findByRole('button', { name: /Baixar meus dados/ }));
    expect(await screen.findByRole('status')).toHaveTextContent('Arquivo com seus dados gerado.');
    expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
    expect(click).toHaveBeenCalled();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:x');
    click.mockRestore();
  });

  it('exclusão exige senha e confirmação; com sucesso encerra a sessão local', async () => {
    vi.mocked(accountApi.getProfile).mockResolvedValue(profile());
    vi.mocked(accountApi.deleteAccount).mockRejectedValueOnce(httpError('Invalid password')).mockResolvedValue({} as never);
    renderWithProviders(<PrivacyPage />);

    const button = await screen.findByRole('button', { name: /Excluir minha conta/ });
    expect(button).toBeDisabled();
    await userEvent.type(screen.getByLabelText('Confirme sua senha'), 'errada');
    await userEvent.click(screen.getByRole('checkbox', { name: /definitiva/ }));
    expect(screen.queryByLabelText('Código do app')).not.toBeInTheDocument();
    await userEvent.click(button);
    expect(await screen.findByRole('alert')).toHaveTextContent('Senha incorreta.');
    expect(accountApi.deleteAccount).toHaveBeenCalledWith('errada', undefined);

    await userEvent.click(button);
    expect(await screen.findByText('HOME PAGE')).toBeInTheDocument();
    expect(localStorage.getItem('token')).toBeNull();
  });

  it('com MFA ativo a exclusão pede o código; erro ao carregar o perfil', async () => {
    localStorage.setItem('user', JSON.stringify({ name: 'Ana', email: 'ana@a.com', role: 'CUSTOMER', mfaEnabled: true }));
    vi.mocked(accountApi.getProfile).mockRejectedValue(new Error('Network Error'));
    vi.mocked(accountApi.deleteAccount).mockResolvedValue({} as never);
    renderWithProviders(<PrivacyPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent(/conectar ao servidor/);
    await userEvent.type(screen.getByLabelText('Confirme sua senha'), 'senha123');
    await userEvent.type(screen.getByLabelText('Código do app'), '12a3456');
    await userEvent.click(screen.getByRole('checkbox', { name: /definitiva/ }));
    await userEvent.click(screen.getByRole('button', { name: /Excluir minha conta/ }));
    await waitFor(() => expect(accountApi.deleteAccount).toHaveBeenCalledWith('senha123', '123456'));
  });
});

describe('SecurityPage (MFA)', () => {
  it('ativa: mostra QR e chave, confirma o código e troca a sessão', async () => {
    vi.mocked(accountApi.setupMfa).mockResolvedValue({ secret: 'JBSWY3DPEHPK3PXP', otpauthUri: 'otpauth://totp/ShopEasy:ana?secret=JBSWY3DPEHPK3PXP' });
    vi.mocked(accountApi.enableMfa).mockRejectedValueOnce(httpError('Invalid MFA code')).mockResolvedValue(auth());
    renderWithProviders(<SecurityPage />);

    const activate = screen.getByRole('button', { name: /Ativar verificação/ });
    expect(activate).toBeDisabled();                                   // revisão 3, A1: exige a senha
    await userEvent.type(screen.getByLabelText('Confirme sua senha'), 'senha123');
    await userEvent.click(activate);
    expect(accountApi.setupMfa).toHaveBeenCalledWith('senha123');
    expect(await screen.findByRole('img', { name: /QR Code para o app/ })).toBeInTheDocument();
    expect(screen.getByText('JBSWY3DPEHPK3PXP')).toBeInTheDocument();
    const confirm = screen.getByRole('button', { name: 'Confirmar e ativar' });
    expect(confirm).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Código do app'), '000000');
    await userEvent.click(confirm);
    expect(await screen.findByRole('alert')).toHaveTextContent('Código de verificação inválido');

    await userEvent.click(confirm);
    expect(await screen.findByRole('status')).toHaveTextContent('ativada');
    expect(localStorage.getItem('token')).toBe('new-token');
    expect(screen.getByText(/Ativa\. Ao entrar/)).toBeInTheDocument();
  });

  it('senha errada ao ativar mostra o erro e não gera QR Code', async () => {
    vi.mocked(accountApi.setupMfa).mockRejectedValue(httpError('Invalid password'));
    renderWithProviders(<SecurityPage />);
    await userEvent.type(screen.getByLabelText('Confirme sua senha'), 'errada');
    await userEvent.click(screen.getByRole('button', { name: /Ativar verificação/ }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Senha incorreta.');
    expect(screen.queryByRole('img', { name: /QR Code/ })).not.toBeInTheDocument();
  });

  it('desativa com senha e código', async () => {
    localStorage.setItem('user', JSON.stringify({ name: 'Ana', email: 'ana@a.com', role: 'CUSTOMER', mfaEnabled: true }));
    vi.mocked(accountApi.disableMfa).mockResolvedValue(auth({ mfaEnabled: false, token: 't3' }));
    renderWithProviders(<SecurityPage />);

    await userEvent.type(screen.getByLabelText('Senha'), 'senha123');
    await userEvent.type(screen.getByLabelText('Código'), '654321');
    await userEvent.click(screen.getByRole('button', { name: 'Desativar' }));
    expect(accountApi.disableMfa).toHaveBeenCalledWith('senha123', '654321');
    expect(await screen.findByRole('status')).toHaveTextContent('desativada');
    expect(screen.getByRole('button', { name: /Ativar verificação/ })).toBeInTheDocument();
  });

  it('admin com MFA obrigatório vê o aviso', () => {
    localStorage.setItem('user', JSON.stringify({ name: 'Chefe', email: 'a@x.com', role: 'ADMIN', mfaSetupRequired: true }));
    renderWithProviders(<SecurityPage />);
    expect(screen.getByRole('note')).toHaveTextContent('Administradores precisam ativar');
  });
});

describe('SecurityPage (troca de senha)', () => {
  const fillPassword = async (current: string, next: string, confirm = next) => {
    await userEvent.type(screen.getByLabelText('Senha atual'), current);
    await userEvent.type(screen.getByLabelText('Nova senha'), next);
    await userEvent.type(screen.getByLabelText('Confirme a nova senha'), confirm);
    await userEvent.click(screen.getByRole('button', { name: 'Alterar senha' }));
  };

  it('troca a senha, troca a sessão e limpa o formulário', async () => {
    vi.mocked(accountApi.changePassword).mockResolvedValue(auth({ mfaEnabled: false, token: 't-nova' }));
    renderWithProviders(<SecurityPage />);
    await fillPassword('senha123', 'novaSenha9');
    expect(accountApi.changePassword).toHaveBeenCalledWith('senha123', 'novaSenha9', '');
    expect(await screen.findByRole('status')).toHaveTextContent('Senha alterada');
    expect(localStorage.getItem('token')).toBe('t-nova');
    expect(screen.getByLabelText('Senha atual')).toHaveValue('');
  });

  it('valida antes de enviar: política de senha e confirmação', async () => {
    renderWithProviders(<SecurityPage />);
    await fillPassword('senha123', 'curta1');
    expect(screen.getByRole('alert')).toHaveTextContent('no mínimo 8');
    await userEvent.clear(screen.getByLabelText('Senha atual'));
    await userEvent.clear(screen.getByLabelText('Nova senha'));
    await userEvent.clear(screen.getByLabelText('Confirme a nova senha'));
    await fillPassword('senha123', 'novaSenha9', 'outraSenha9');
    expect(screen.getByRole('alert')).toHaveTextContent('As senhas não conferem.');
    expect(accountApi.changePassword).not.toHaveBeenCalled();
  });

  it('com MFA pede o código; erro do back-end é traduzido', async () => {
    localStorage.setItem('user', JSON.stringify({ name: 'Ana', email: 'ana@a.com', role: 'CUSTOMER', mfaEnabled: true }));
    vi.mocked(accountApi.changePassword).mockRejectedValue(httpError('New password must be different from the current one'));
    renderWithProviders(<SecurityPage />);
    await userEvent.type(screen.getByLabelText('Código do app'), '123456');
    await fillPassword('senha123', 'senha1234');
    expect(accountApi.changePassword).toHaveBeenCalledWith('senha123', 'senha1234', '123456');
    expect(await screen.findByRole('alert')).toHaveTextContent('diferente da atual');
  });
});
