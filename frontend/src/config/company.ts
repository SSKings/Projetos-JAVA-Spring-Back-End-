/**
 * Identificação do fornecedor (Decreto 7.962/2013, art. 2º) e canal do encarregado (LGPD art. 41).
 * Preencha as variáveis VITE_COMPANY_* no build de produção (ver .env.example). Enquanto houver campo
 * entre colchetes, as páginas legais exibem um aviso de "modelo não preenchido".
 */
const env = import.meta.env;

export const company = {
  legalName: env.VITE_COMPANY_LEGAL_NAME || '[Razão social]',
  tradeName: env.VITE_COMPANY_TRADE_NAME || 'ShopEasy',
  cnpj: env.VITE_COMPANY_CNPJ || '[CNPJ]',
  address: env.VITE_COMPANY_ADDRESS || '[Endereço completo]',
  email: env.VITE_COMPANY_EMAIL || '[email de atendimento]',
  phone: env.VITE_COMPANY_PHONE || '[telefone]',
  dpoEmail: env.VITE_COMPANY_DPO_EMAIL || '[email do encarregado / canal LGPD]',
};

export const isCompanyConfigured = () => !Object.values(company).some(v => v.startsWith('['));

/** Deve ser igual a app.terms-version (TERMS_VERSION) no back-end. */
export const TERMS_VERSION = env.VITE_TERMS_VERSION || '2026-11-05';
