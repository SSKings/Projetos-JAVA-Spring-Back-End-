import { useState } from 'react';
import { X } from 'lucide-react';
import { createProduct, updateProduct } from '../../api/admin';
import type { AdminProduct, ProductInput } from '../../types';
import { getErrorMessage } from '../../utils/format';

interface Props {
  product: AdminProduct | null; // null = novo produto
  onClose: () => void;
  onSaved: () => void;
}

export default function ProductFormModal({ product, onClose, onSaved }: Props) {
  const [form, setForm] = useState({
    name: product?.name ?? '',
    description: product?.description ?? '',
    imageUrl: product?.imageUrl ?? '',
    price: product ? String(product.price) : '',
    stockQuantity: product ? String(product.stockQuantity) : '0',
    active: product?.active ?? true,
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const set = (key: keyof typeof form, value: string | boolean) => setForm(f => ({ ...f, [key]: value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const data: ProductInput = {
      name: form.name,
      description: form.description,
      imageUrl: form.imageUrl,
      price: Number(form.price.replace(',', '.')),
      stockQuantity: Number(form.stockQuantity),
      active: form.active,
    };
    setSaving(true);
    setError('');
    try {
      if (product) await updateProduct(product.id, data);
      else await createProduct(data);
      onSaved();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const input = 'w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500';

  return (
    <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-y-auto"
           onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="text-lg font-semibold text-gray-900">{product ? 'Editar produto' : 'Novo produto'}</h2>
          <button onClick={onClose} className="p-1 text-gray-400 hover:text-gray-700" aria-label="Fechar"><X size={20} /></button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && <div className="px-4 py-3 bg-red-50 text-red-700 rounded-lg text-sm">{error}</div>}

          <div>
            <label htmlFor="pf-name" className="block text-sm font-medium text-gray-700 mb-1">Nome</label>
            <input id="pf-name" className={input} required maxLength={150} value={form.name} onChange={e => set('name', e.target.value)} />
          </div>
          <div>
            <label htmlFor="pf-description" className="block text-sm font-medium text-gray-700 mb-1">Descrição</label>
            <textarea id="pf-description" className={input} rows={3} value={form.description} onChange={e => set('description', e.target.value)} />
          </div>
          <div>
            <label htmlFor="pf-imageUrl" className="block text-sm font-medium text-gray-700 mb-1">URL da imagem</label>
            <input id="pf-imageUrl" className={input} type="url" maxLength={255} placeholder="https://..." value={form.imageUrl}
                   onChange={e => set('imageUrl', e.target.value)} />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="pf-price" className="block text-sm font-medium text-gray-700 mb-1">Preço (R$)</label>
              <input id="pf-price" className={input} required type="number" min="0.01" step="0.01" value={form.price}
                     onChange={e => set('price', e.target.value)} />
            </div>
            <div>
              <label htmlFor="pf-stockQuantity" className="block text-sm font-medium text-gray-700 mb-1">Estoque</label>
              <input id="pf-stockQuantity" className={input} required type="number" min="0" step="1" value={form.stockQuantity}
                     onChange={e => set('stockQuantity', e.target.value)} />
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input type="checkbox" checked={form.active} onChange={e => set('active', e.target.checked)}
                   className="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500" />
            Visível na loja
          </label>

          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={onClose}
                    className="px-4 py-2 text-sm rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50">
              Cancelar
            </button>
            <button type="submit" disabled={saving}
                    className="px-4 py-2 text-sm rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white font-medium disabled:opacity-60">
              {saving ? 'Salvando...' : 'Salvar'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
