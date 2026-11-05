import type { InputHTMLAttributes } from 'react';

interface Props extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  compact?: boolean;
}

/** Input com label, dica e mensagem de erro ligados por aria-describedby (acessível a leitores de tela). */
export default function FormField({ id, label, error, hint, compact = false, className = '', ...input }: Props) {
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div className={className}>
      <label htmlFor={id} className={compact ? 'text-xs font-medium text-gray-600 block mb-1' : 'block text-sm font-medium text-gray-700 mb-1.5'}>
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={`w-full border rounded-lg text-sm focus:outline-none focus:ring-2 ${
          compact ? 'px-3 py-2' : 'px-4 py-2.5'
        } ${error ? 'border-red-400 focus:ring-red-400' : 'border-gray-300 focus:ring-indigo-500'}`}
        {...input}
      />
      {hint && !error && <p id={`${id}-hint`} className="text-xs text-gray-400 mt-1">{hint}</p>}
      {error && <p id={`${id}-error`} role="alert" className="text-xs text-red-600 mt-1">{error}</p>}
    </div>
  );
}
