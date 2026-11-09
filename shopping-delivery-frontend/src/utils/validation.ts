import type { AddressPayload } from '../api/addresses';

/**
 * Validações dos formulários de cadastro e de endereço.
 *
 * Os limites de tamanho espelham as colunas do banco (V1__init.sql) e as anotações @Size dos
 * records RegisterRequest / AddressRequest do back-end — o front-end avisa antes, o back-end garante.
 */

export type FieldErrors<T> = Partial<Record<keyof T, string>>;

export const LIMITS = {
  name: 100,
  email: 150,
  passwordMin: 8,
  passwordMax: 72, // BCrypt ignora o que passa de 72 bytes
  street: 150,
  number: 10,
  complement: 100,
  neighborhood: 100,
  city: 100,
  unitsPerItem: 10, // CartService.MAX_UNITS_PER_ITEM: teto anti-açambarcamento de estoque (revisão 3, A3)
} as const;

/** Siglas das 27 unidades federativas. */
export const UFS = [
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA',
  'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO',
] as const;

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const PERSON_NAME_RE = /^[\p{L}][\p{L}\s'.-]*$/u;
const CEP_RE = /^\d{5}-\d{3}$/;
const HOUSE_NUMBER_RE = /^(\d+[\p{L}\d\s/-]*|s\/n)$/iu;

export const onlyDigits = (v: string) => v.replace(/\D/g, '');

/** "01310100" → "01310-100" (aceita digitação parcial). */
export function maskCEP(value: string): string {
  const d = onlyDigits(value).slice(0, 8);
  return d.length > 5 ? `${d.slice(0, 5)}-${d.slice(5)}` : d;
}

/** "11987654321" → "(11) 98765-4321"; com 10 dígitos → "(11) 3456-7890". */
export function maskPhone(value: string): string {
  const d = onlyDigits(value).slice(0, 11);
  if (d.length === 0) return '';
  if (d.length <= 2) return `(${d}`;
  const ddd = d.slice(0, 2);
  const rest = d.slice(2);
  const split = d.length === 11 ? 5 : 4;
  return rest.length > split ? `(${ddd}) ${rest.slice(0, split)}-${rest.slice(split)}` : `(${ddd}) ${rest}`;
}

// ---------- Senha (cadastro e troca de senha) ----------
/**
 * Mesma política do back-end (RegisterRequest/ChangePasswordRequest). O limite de 72 é em BYTES (BCrypt):
 * "ç", "ã" ou emoji ocupam 2 a 4 — o back-end recusa (@BcryptSafe), então o front avisa antes.
 */
export function validatePassword(password: string): string | undefined {
  if (!password) return 'Informe uma senha.';
  if (password.length < LIMITS.passwordMin) return `A senha deve ter no mínimo ${LIMITS.passwordMin} caracteres.`;
  if (password.length > LIMITS.passwordMax) return `A senha deve ter no máximo ${LIMITS.passwordMax} caracteres.`;
  if (new TextEncoder().encode(password).length > LIMITS.passwordMax) {
    return 'A senha é longa demais: letras acentuadas e emojis contam como 2 ou mais caracteres.';
  }
  if (!/\p{L}/u.test(password) || !/\d/.test(password)) return 'A senha deve conter letras e números.';
  return undefined;
}

// ---------- Cadastro ----------
export interface RegisterValues {
  name: string;
  email: string;
  password: string;
  confirmPassword: string;
  phone: string;
}

export function validateRegister(v: RegisterValues): FieldErrors<RegisterValues> {
  const errors: FieldErrors<RegisterValues> = {};
  const name = v.name.trim();
  const email = v.email.trim();

  if (!name) errors.name = 'Informe seu nome.';
  else if (name.length > LIMITS.name) errors.name = `O nome deve ter no máximo ${LIMITS.name} caracteres.`;
  else if (!PERSON_NAME_RE.test(name)) errors.name = 'O nome deve conter apenas letras.';
  else if (name.split(/\s+/).length < 2) errors.name = 'Informe nome e sobrenome.';

  if (!email) errors.email = 'Informe seu email.';
  else if (email.length > LIMITS.email) errors.email = `O email deve ter no máximo ${LIMITS.email} caracteres.`;
  else if (!EMAIL_RE.test(email)) errors.email = 'Informe um email válido, ex.: nome@dominio.com.';
  else if (/\.invalid$/i.test(email)) errors.email = 'Este domínio de email não é aceito.';
  else if (!/^[\x21-\x7E]+$/.test(email)) errors.email = 'Use só letras sem acento, números e símbolos no email.';

  const passwordError = validatePassword(v.password);
  if (passwordError) errors.password = passwordError;

  if (!v.confirmPassword) errors.confirmPassword = 'Confirme a senha.';
  else if (v.confirmPassword !== v.password) errors.confirmPassword = 'As senhas não conferem.';

  const phoneDigits = onlyDigits(v.phone).length;
  if (v.phone.trim() && phoneDigits !== 10 && phoneDigits !== 11) {
    errors.phone = 'Telefone inválido. Use DDD + número, ex.: (11) 99999-9999.';
  }

  return errors;
}

// ---------- Endereço ----------
export interface AddressValues {
  zipCode: string;
  street: string;
  number: string;
  complement: string;
  neighborhood: string;
  city: string;
  state: string;
}

const required = (label: string, value: string, max: number, min = 1): string | undefined => {
  const v = value.trim();
  if (!v) return `Informe ${label}.`;
  if (v.length < min) return `Deve ter no mínimo ${min} caracteres.`;
  if (v.length > max) return `Deve ter no máximo ${max} caracteres.`;
  return undefined;
};

export function validateAddress(v: AddressValues): FieldErrors<AddressValues> {
  const errors: FieldErrors<AddressValues> = {};
  const set = (key: keyof AddressValues, msg?: string) => { if (msg) errors[key] = msg; };

  if (!v.zipCode.trim()) errors.zipCode = 'Informe o CEP.';
  else if (!CEP_RE.test(v.zipCode.trim())) errors.zipCode = 'CEP inválido. Use o formato 00000-000.';

  set('street', required('a rua', v.street, LIMITS.street, 3));

  const number = v.number.trim();
  if (!number) errors.number = 'Informe o número (ou S/N).';
  else if (number.length > LIMITS.number) errors.number = `Deve ter no máximo ${LIMITS.number} caracteres.`;
  else if (!HOUSE_NUMBER_RE.test(number)) errors.number = 'Número inválido. Ex.: 123, 45A ou S/N.';

  if (v.complement.trim().length > LIMITS.complement) {
    errors.complement = `Deve ter no máximo ${LIMITS.complement} caracteres.`;
  }

  set('neighborhood', required('o bairro', v.neighborhood, LIMITS.neighborhood, 2));

  const cityError = required('a cidade', v.city, LIMITS.city, 2);
  if (cityError) errors.city = cityError;
  else if (!PERSON_NAME_RE.test(v.city.trim())) errors.city = 'A cidade deve conter apenas letras.';

  const uf = v.state.trim().toUpperCase();
  if (!uf) errors.state = 'Informe a UF.';
  else if (!(UFS as readonly string[]).includes(uf)) errors.state = 'UF inválida. Ex.: SP, RJ, MG.';

  return errors;
}

/** Normaliza o endereço para envio: trim, UF maiúscula, "s/n" → "S/N" e complemento vazio omitido. */
export function toAddressPayload(v: AddressValues): AddressPayload {
  const complement = v.complement.trim();
  return {
    zipCode: v.zipCode.trim(),
    street: v.street.trim(),
    number: v.number.trim().toUpperCase() === 'S/N' ? 'S/N' : v.number.trim(),
    ...(complement ? { complement } : {}),
    neighborhood: v.neighborhood.trim(),
    city: v.city.trim(),
    state: v.state.trim().toUpperCase(),
  };
}

export const hasErrors = <T,>(errors: FieldErrors<T>) => Object.keys(errors).length > 0;
