import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AddressForm from './AddressForm';

const field = (label: string) => screen.getByLabelText(label);

async function fillValid() {
  await userEvent.type(field('CEP'), '01310100');
  await userEvent.type(field('Rua'), ' Av. Paulista ');
  await userEvent.type(field('Número'), 's/n');
  await userEvent.type(field('Bairro'), 'Bela Vista');
  await userEvent.type(field('Cidade'), 'São Paulo');
  await userEvent.type(field('UF'), 's1p');
}

describe('AddressForm', () => {
  it('aplica máscara de CEP e UF', async () => {
    render(<AddressForm onSubmit={vi.fn()} />);
    await userEvent.type(field('CEP'), '01310100999');
    await userEvent.type(field('UF'), 'r-j9x');
    expect(field('CEP')).toHaveValue('01310-100');
    expect(field('UF')).toHaveValue('RJ');
  });

  it('envio vazio mostra todos os erros e não chama onSubmit', async () => {
    const onSubmit = vi.fn();
    render(<AddressForm onSubmit={onSubmit} />);
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(screen.getAllByRole('alert')).toHaveLength(6);
    expect(screen.getByText('Informe o CEP.')).toBeInTheDocument();
    expect(field('CEP')).toHaveAttribute('aria-invalid', 'true');
    expect(field('CEP')).toHaveAccessibleDescription('Informe o CEP.');
    expect(field('Complemento (opcional)')).not.toHaveAttribute('aria-invalid');
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('erro aparece ao sair do campo e some quando corrigido', async () => {
    render(<AddressForm onSubmit={vi.fn()} />);
    await userEvent.type(field('CEP'), '0100');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    await userEvent.tab();
    expect(screen.getByText('CEP inválido. Use o formato 00000-000.')).toBeInTheDocument();
    await userEvent.type(field('CEP'), '0000');
    expect(screen.queryByText(/CEP inválido/)).not.toBeInTheDocument();
  });

  it('envia o endereço normalizado e limpa o formulário', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<AddressForm onSubmit={onSubmit} submitLabel="Cadastrar" />);
    await fillValid();
    await userEvent.click(screen.getByRole('button', { name: 'Cadastrar' }));

    expect(onSubmit).toHaveBeenCalledWith({
      zipCode: '01310-100', street: 'Av. Paulista', number: 'S/N', neighborhood: 'Bela Vista', city: 'São Paulo', state: 'SP',
    });
    expect(field('CEP')).toHaveValue('');
  });

  it('mostra o erro do back-end e mantém os dados', async () => {
    const onSubmit = vi.fn().mockRejectedValue({ response: { status: 400, data: { message: 'Validation failed', errors: ['Street must have at most 150 characters'] } } });
    render(<AddressForm onSubmit={onSubmit} />);
    await fillValid();
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Street must have at most 150 characters')).toBeInTheDocument();
    expect(field('CEP')).toHaveValue('01310-100');
  });

  it('botão Cancelar só aparece com onCancel', async () => {
    const onCancel = vi.fn();
    const { rerender } = render(<AddressForm onSubmit={vi.fn()} />);
    expect(screen.queryByRole('button', { name: 'Cancelar' })).not.toBeInTheDocument();
    rerender(<AddressForm onSubmit={vi.fn()} onCancel={onCancel} />);
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(onCancel).toHaveBeenCalled();
  });
});
