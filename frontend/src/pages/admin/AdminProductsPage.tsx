import { useCallback, useEffect, useState } from 'react';
import { Pencil, Plus, Search } from 'lucide-react';
import { deactivateProduct, getAdminProducts, updateProduct } from '../../api/admin';
import Pagination from '../../components/Pagination';
import type { AdminProduct, Page } from '../../types';
import { formatBRL, getErrorMessage } from '../../utils/format';
import ProductFormModal from './ProductFormModal';

export default function AdminProductsPage() {
  const [data, setData] = useState<Page<AdminProduct> | null>(null);
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [error, setError] = useState('');
  const [editing, setEditing] = useState<AdminProduct | null | undefined>(undefined); // undefined = modal fechado

  const load = useCallback(() => {
    getAdminProducts(page, query).then(setData).catch(err => setError(getErrorMessage(err)));
  }, [page, query]);

  useEffect(load, [load]);

  const toggleActive = async (p: AdminProduct) => {
    setError('');
    try {
      if (p.active) await deactivateProduct(p.id);
      else await updateProduct(p.id, { ...p, description: p.description ?? '', imageUrl: p.imageUrl ?? '', active: true });
      load();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  return (
    <div>
      <div className="flex flex-col sm:flex-row gap-3 sm:items-center sm:justify-between mb-4">
        <form onSubmit={e => { e.preventDefault(); setPage(0); setQuery(search); }} className="relative w-full sm:max-w-xs">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Buscar produto..."
                 className="w-full pl-9 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
        </form>
        <button onClick={() => setEditing(null)}
                className="flex items-center justify-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-medium">
          <Plus size={16} /> Novo produto
        </button>
      </div>

      {error && <div className="mb-4 px-4 py-3 bg-red-50 text-red-700 rounded-lg text-sm">{error}</div>}

      <div className="bg-white rounded-xl border border-gray-200 overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-500 text-left">
            <tr>
              <th className="px-4 py-3 font-medium">Produto</th>
              <th className="px-4 py-3 font-medium">Preço</th>
              <th className="px-4 py-3 font-medium">Estoque</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium text-right">Ações</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {data?.content.map(p => (
              <tr key={p.id} className={p.active ? '' : 'bg-gray-50/60 text-gray-400'}>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-3">
                    {p.imageUrl
                      ? <img src={p.imageUrl} alt="" className="w-10 h-10 rounded-lg object-cover bg-gray-100" />
                      : <div className="w-10 h-10 rounded-lg bg-gray-100" />}
                    <span className="font-medium text-gray-900">{p.name}</span>
                  </div>
                </td>
                <td className="px-4 py-3 whitespace-nowrap">{formatBRL(p.price)}</td>
                <td className={`px-4 py-3 ${p.stockQuantity <= 5 ? 'text-amber-600 font-semibold' : ''}`}>{p.stockQuantity}</td>
                <td className="px-4 py-3">
                  <span className={`text-xs font-medium px-2.5 py-0.5 rounded-full ${p.active ? 'bg-green-100 text-green-700' : 'bg-gray-200 text-gray-600'}`}>
                    {p.active ? 'À venda' : 'Inativo'}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <div className="flex justify-end gap-2">
                    <button onClick={() => setEditing(p)} className="p-2 text-gray-500 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg" aria-label="Editar">
                      <Pencil size={16} />
                    </button>
                    <button onClick={() => toggleActive(p)}
                            className={`px-3 py-1.5 text-xs rounded-lg border ${p.active ? 'border-red-200 text-red-600 hover:bg-red-50' : 'border-green-200 text-green-700 hover:bg-green-50'}`}>
                      {p.active ? 'Desativar' : 'Reativar'}
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {data && data.content.length === 0 && (
              <tr><td colSpan={5} className="px-4 py-10 text-center text-gray-400">Nenhum produto encontrado</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {data && <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />}

      {editing !== undefined && (
        <ProductFormModal product={editing} onClose={() => setEditing(undefined)}
                          onSaved={() => { setEditing(undefined); load(); }} />
      )}
    </div>
  );
}
