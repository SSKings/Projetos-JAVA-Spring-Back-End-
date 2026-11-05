import { useState } from 'react';
import { hasErrors, type FieldErrors } from '../utils/validation';

/**
 * Estado de formulário com validação.
 * O erro de um campo só aparece depois que ele perde o foco (blur) ou após a primeira tentativa de envio;
 * a partir daí é recalculado a cada digitação.
 */
export function useValidatedForm<T extends { [K in keyof T]: string }>(initial: T, validate: (v: T) => FieldErrors<T>) {
  const [values, setValues] = useState<T>(initial);
  const [touched, setTouched] = useState<Partial<Record<keyof T, boolean>>>({});

  const errors = validate(values);

  const setValue = (key: keyof T, value: string) => setValues(v => ({ ...v, [key]: value }));
  const touch = (key: keyof T) => setTouched(t => ({ ...t, [key]: true }));
  const errorOf = (key: keyof T) => (touched[key] ? errors[key] : undefined);

  /** Marca todos os campos como tocados e diz se o formulário pode ser enviado. */
  const validateAll = () => {
    setTouched(Object.fromEntries(Object.keys(values).map(k => [k, true])) as Record<keyof T, boolean>);
    return !hasErrors(errors);
  };

  const reset = () => {
    setValues(initial);
    setTouched({});
  };

  return { values, errors, setValue, touch, errorOf, validateAll, reset };
}
