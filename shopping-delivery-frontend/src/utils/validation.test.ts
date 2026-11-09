import { describe, expect, it } from 'vitest';
import {
  hasErrors, maskCEP, maskPhone, onlyDigits, toAddressPayload, validateAddress, validateRegister,
  type AddressValues, type RegisterValues,
} from './validation';

const reg = (over: Partial<RegisterValues> = {}): RegisterValues => ({
  name: 'Ana Souza', email: 'ana@a.com', password: 'abc12345', confirmPassword: 'abc12345', phone: '', ...over,
});

const addr = (over: Partial<AddressValues> = {}): AddressValues => ({
  zipCode: '01000-000', street: 'Rua A', number: '10', complement: '', neighborhood: 'Centro',
  city: 'São Paulo', state: 'SP', ...over,
});

describe('máscaras', () => {
  it('onlyDigits remove tudo que não é número', () => {
    expect(onlyDigits('(11) 9.8-7')).toBe('11987');
  });

  it.each([
    ['', ''], ['0100', '0100'], ['01000', '01000'], ['010001', '01000-1'], ['01000000', '01000-000'],
    ['01000-0009999', '01000-000'], ['ab01000x000', '01000-000'],
  ])('maskCEP(%j) = %j', (input, expected) => {
    expect(maskCEP(input)).toBe(expected);
  });

  it.each([
    ['', ''], ['1', '(1'], ['11', '(11'], ['119', '(11) 9'], ['113456', '(11) 3456'],
    ['1134567890', '(11) 3456-7890'], ['11987654321', '(11) 98765-4321'], ['119876543210000', '(11) 98765-4321'],
  ])('maskPhone(%j) = %j', (input, expected) => {
    expect(maskPhone(input)).toBe(expected);
  });
});

describe('validateRegister', () => {
  it('dados válidos não geram erros (telefone é opcional)', () => {
    expect(validateRegister(reg())).toEqual({});
    expect(validateRegister(reg({ phone: '(11) 98765-4321' }))).toEqual({});
    expect(validateRegister(reg({ phone: '(11) 3456-7890', name: "  Maria D'Ávila-Neto " }))).toEqual({});
  });

  it.each([
    [{ name: '' }, 'name', 'Informe seu nome.'],
    [{ name: '   ' }, 'name', 'Informe seu nome.'],
    [{ name: 'Ana' }, 'name', 'Informe nome e sobrenome.'],
    [{ name: 'Ana 123' }, 'name', 'O nome deve conter apenas letras.'],
    [{ name: `Ana ${'a'.repeat(100)}` }, 'name', 'O nome deve ter no máximo 100 caracteres.'],
    [{ email: '' }, 'email', 'Informe seu email.'],
    [{ email: 'ana@a' }, 'email', 'Informe um email válido, ex.: nome@dominio.com.'],
    [{ email: 'ana a@a.com' }, 'email', 'Informe um email válido, ex.: nome@dominio.com.'],
    [{ email: `${'a'.repeat(145)}@a.com` }, 'email', 'O email deve ter no máximo 150 caracteres.'],
    [{ email: 'removed-1@anonymized.INVALID' }, 'email', 'Este domínio de email não é aceito.'],
    [{ email: '\u0430dmin@shopeasy.com' }, 'email', 'Use só letras sem acento, números e símbolos no email.'],
    [{ password: '', confirmPassword: '' }, 'password', 'Informe uma senha.'],
    [{ password: 'abc1234', confirmPassword: 'abc1234' }, 'password', 'A senha deve ter no mínimo 8 caracteres.'],
    [{ password: `a1${'x'.repeat(71)}` }, 'password', 'A senha deve ter no máximo 72 caracteres.'],
    [{ password: 'abcdefgh', confirmPassword: 'abcdefgh' }, 'password', 'A senha deve conter letras e números.'],
    [{ password: `${'ç'.repeat(36)}1` }, 'password', 'A senha é longa demais: letras acentuadas e emojis contam como 2 ou mais caracteres.'],
    [{ password: '12345678', confirmPassword: '12345678' }, 'password', 'A senha deve conter letras e números.'],
    [{ confirmPassword: '' }, 'confirmPassword', 'Confirme a senha.'],
    [{ confirmPassword: 'abc12346' }, 'confirmPassword', 'As senhas não conferem.'],
    [{ phone: '1199' }, 'phone', 'Telefone inválido. Use DDD + número, ex.: (11) 99999-9999.'],
  ] as const)('%j → %s: %s', (over, field, message) => {
    const errors = validateRegister(reg(over));
    expect(errors[field]).toBe(message);
  });
});

describe('validateAddress', () => {
  it('endereço válido (complemento opcional, S/N aceito, UF minúscula)', () => {
    expect(validateAddress(addr())).toEqual({});
    expect(validateAddress(addr({ number: 's/n', state: 'rj', complement: 'Apto 4' }))).toEqual({});
    expect(validateAddress(addr({ number: '1200-B' }))).toEqual({});
  });

  it('formulário vazio aponta todos os obrigatórios', () => {
    const errors = validateAddress(addr({ zipCode: '', street: '', number: '', neighborhood: '', city: '', state: '' }));
    expect(Object.keys(errors).sort()).toEqual(['city', 'neighborhood', 'number', 'state', 'street', 'zipCode']);
    expect(errors.street).toBe('Informe a rua.');
    expect(errors.number).toBe('Informe o número (ou S/N).');
  });

  it.each([
    [{ zipCode: '01000' }, 'zipCode', 'CEP inválido. Use o formato 00000-000.'],
    [{ zipCode: '01000000' }, 'zipCode', 'CEP inválido. Use o formato 00000-000.'],
    [{ street: 'R' }, 'street', 'Deve ter no mínimo 3 caracteres.'],
    [{ street: 'R'.repeat(151) }, 'street', 'Deve ter no máximo 150 caracteres.'],
    [{ number: 'abc' }, 'number', 'Número inválido. Ex.: 123, 45A ou S/N.'],
    [{ number: '12345678901' }, 'number', 'Deve ter no máximo 10 caracteres.'],
    [{ complement: 'c'.repeat(101) }, 'complement', 'Deve ter no máximo 100 caracteres.'],
    [{ neighborhood: 'C' }, 'neighborhood', 'Deve ter no mínimo 2 caracteres.'],
    [{ city: 'São Paulo 2' }, 'city', 'A cidade deve conter apenas letras.'],
    [{ city: 'c'.repeat(101) }, 'city', 'Deve ter no máximo 100 caracteres.'],
    [{ state: 'XX' }, 'state', 'UF inválida. Ex.: SP, RJ, MG.'],
  ] as const)('%j → %s: %s', (over, field, message) => {
    expect(validateAddress(addr(over))[field]).toBe(message);
  });
});

describe('toAddressPayload', () => {
  it('normaliza espaços, UF, S/N e omite complemento vazio', () => {
    expect(toAddressPayload(addr({ street: ' Rua A ', number: 's/n', state: 'sp', complement: '  ' }))).toEqual({
      zipCode: '01000-000', street: 'Rua A', number: 'S/N', neighborhood: 'Centro', city: 'São Paulo', state: 'SP',
    });
    expect(toAddressPayload(addr({ complement: ' Apto 4 ' })).complement).toBe('Apto 4');
  });
});

it('hasErrors', () => {
  expect(hasErrors({})).toBe(false);
  expect(hasErrors({ name: 'x' })).toBe(true);
});
