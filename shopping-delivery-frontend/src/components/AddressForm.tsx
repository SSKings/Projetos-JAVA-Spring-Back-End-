import { useState } from 'react';
import FormField from './FormField';
import { useValidatedForm } from '../hooks/useValidatedForm';
import { LIMITS, maskCEP, toAddressPayload, validateAddress, type AddressValues } from '../utils/validation';
import { getErrorMessage } from '../utils/format';
import type { AddressPayload } from '../api/addresses';

interface Props {
  title?: string;
  submitLabel?: string;
  /** Recebe o endereço já normalizado (trim, UF maiúscula, complemento vazio omitido). */
  onSubmit: (payload: AddressPayload) => Promise<void>;
  onCancel?: () => void;
}

const empty: AddressValues = {
  zipCode: '', street: '', number: '', complement: '', neighborhood: '', city: '', state: '',
};

type Field = {
  key: keyof AddressValues;
  label: string;
  placeholder: string;
  wide?: boolean;
  maxLength?: number;
  inputMode?: 'numeric';
  autoComplete?: string;
};

const fields: Field[] = [
  { key: 'zipCode', label: 'CEP', placeholder: '00000-000', maxLength: 9, inputMode: 'numeric', autoComplete: 'postal-code' },
  { key: 'street', label: 'Rua', placeholder: 'Rua das Flores', wide: true, maxLength: LIMITS.street, autoComplete: 'address-line1' },
  { key: 'number', label: 'Número', placeholder: '123', maxLength: LIMITS.number },
  { key: 'complement', label: 'Complemento (opcional)', placeholder: 'Apto 4', maxLength: LIMITS.complement, autoComplete: 'address-line2' },
  { key: 'neighborhood', label: 'Bairro', placeholder: 'Centro', maxLength: LIMITS.neighborhood },
  { key: 'city', label: 'Cidade', placeholder: 'São Paulo', maxLength: LIMITS.city, autoComplete: 'address-level2' },
  { key: 'state', label: 'UF', placeholder: 'SP', maxLength: 2, autoComplete: 'address-level1' },
];

export default function AddressForm({ title = 'Novo endereço', submitLabel = 'Salvar', onSubmit, onCancel }: Props) {
  const form = useValidatedForm(empty, validateAddress);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const handleChange = (key: keyof AddressValues, raw: string) => {
    const value = key === 'zipCode' ? maskCEP(raw) : key === 'state' ? raw.toUpperCase().replace(/[^A-Z]/g, '') : raw;
    form.setValue(key, value);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.validateAll()) return;
    setSaving(true);
    setError('');
    try {
      await onSubmit(toAddressPayload(form.values));
      form.reset();
    } catch (err) {
      setError(getErrorMessage(err, 'Erro ao salvar endereço.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} noValidate aria-label={title} className="bg-white border border-gray-200 rounded-xl p-5 space-y-3">
      <h3 className="font-semibold text-gray-900 text-sm">{title}</h3>
      {error && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">{error}</p>}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {fields.map(f => (
          <FormField
            key={f.key}
            compact
            id={`address-${f.key}`}
            className={f.wide ? 'sm:col-span-2' : ''}
            label={f.label}
            placeholder={f.placeholder}
            maxLength={f.maxLength}
            inputMode={f.inputMode}
            autoComplete={f.autoComplete}
            value={form.values[f.key]}
            error={form.errorOf(f.key)}
            onChange={e => handleChange(f.key, e.target.value)}
            onBlur={() => form.touch(f.key)}
          />
        ))}
      </div>
      <div className="flex gap-2 pt-1">
        {onCancel && (
          <button type="button" onClick={onCancel}
            className="flex-1 py-2 border border-gray-300 rounded-lg text-sm text-gray-600 hover:bg-gray-50">
            Cancelar
          </button>
        )}
        <button type="submit" disabled={saving}
          className="flex-1 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-medium disabled:opacity-60">
          {saving ? 'Salvando...' : submitLabel}
        </button>
      </div>
    </form>
  );
}
