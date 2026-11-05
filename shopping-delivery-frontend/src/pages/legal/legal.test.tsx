import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PrivacyPolicyPage, ReturnsPolicyPage, TermsPage } from './LegalPages';
import Footer from '../../components/Footer';

const inRouter = (ui: React.ReactElement) => render(<MemoryRouter>{ui}</MemoryRouter>);

afterEach(() => {
  vi.unstubAllEnvs();
  vi.resetModules();
});

describe('Páginas legais', () => {
  it('política de privacidade cobre dados, bases legais, retenção, direitos e encarregado', () => {
    inRouter(<PrivacyPolicyPage />);
    expect(screen.getByRole('heading', { name: 'Política de privacidade' })).toBeInTheDocument();
    for (const text of [/execução de contrato/, /obrigação legal/, /6 meses/, /art\. 18/, /ANPD/]) {
      expect(screen.getAllByText(text).length).toBeGreaterThan(0);
    }
    expect(screen.getByRole('link', { name: /Dados e privacidade/ })).toHaveAttribute('href', '/privacy');
  });

  it('termos e trocas mencionam arrependimento de 7 dias (CDC art. 49)', () => {
    inRouter(<TermsPage />);
    expect(screen.getByText(/7 dias/)).toBeInTheDocument();
    inRouter(<ReturnsPolicyPage />);
    expect(screen.getAllByText(/art\. 49/).length).toBeGreaterThan(0);
    expect(screen.getByText(/30 dias/)).toBeInTheDocument();
  });

  it('sem dados da empresa mostra aviso de modelo e placeholders no rodapé', () => {
    inRouter(<><TermsPage /><Footer /></>);
    expect(screen.getByRole('note')).toHaveTextContent('Modelo ainda não preenchido');
    expect(screen.getByRole('contentinfo')).toHaveTextContent('[Razão social] — CNPJ [CNPJ]');
    expect(screen.getByRole('link', { name: 'Trocas e devoluções' })).toHaveAttribute('href', '/trocas-e-devolucoes');
  });

  it('com VITE_COMPANY_* preenchidas o aviso some e o rodapé identifica a empresa', async () => {
    const vars = {
      VITE_COMPANY_LEGAL_NAME: 'Loja Exemplo Ltda', VITE_COMPANY_CNPJ: '12.345.678/0001-99', VITE_COMPANY_ADDRESS: 'Rua X, 1 - SP',
      VITE_COMPANY_EMAIL: 'sac@loja.com.br', VITE_COMPANY_PHONE: '(11) 3000-0000', VITE_COMPANY_DPO_EMAIL: 'privacidade@loja.com.br',
    };
    for (const [k, v] of Object.entries(vars)) vi.stubEnv(k, v);
    const legal = await import('./LegalPages');
    const footer = await import('../../components/Footer');
    inRouter(<><legal.PrivacyPolicyPage /><footer.default /></>);
    expect(screen.queryByRole('note')).not.toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toHaveTextContent('Loja Exemplo Ltda — CNPJ 12.345.678/0001-99');
    expect(screen.getAllByText(/privacidade@loja\.com\.br/).length).toBeGreaterThan(0);
  });
});
