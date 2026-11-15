import { useEffect, useState } from 'react';
import { Search, ChevronLeft, ChevronRight } from 'lucide-react';
import { getProducts } from '../api/products';
import ProductCard from '../components/ProductCard';
import type { Product } from '../types';

interface Query {
  page: number;
  search: string;
}

interface Loaded {
  /** Consulta a que esta resposta corresponde (ver `loading`). */
  key: string;
  page: number;
  products: Product[];
  totalPages: number;
}

const keyOf = (q: Query) => `${q.page}|${q.search}`;

export default function ProductListPage() {
  const [query, setQuery] = useState<Query>({ page: 0, search: '' });
  const [inputValue, setInputValue] = useState('');
  const [loaded, setLoaded] = useState<Loaded | null>(null);

  const key = keyOf(query);
  // "Carregando" é derivado (a última resposta não é da consulta atual), não um estado ligado dentro do efeito:
  // setState síncrono no useEffect provoca um render extra em cascata (regra react/set-state-in-effect).
  const loading = loaded?.key !== key;

  useEffect(() => {
    // Ignora respostas de consultas antigas: sem isso, uma busca lenta que termina depois da mais nova
    // sobrescrevia o resultado na tela. AbortController em vez de "let current = false" (regra react/immutability).
    const stale = new AbortController();
    getProducts(query.page, 12, query.search || undefined)
      .then(data => {
        if (!stale.signal.aborted) setLoaded({ key, page: query.page, products: data.content, totalPages: data.totalPages });
      })
      .catch(() => {
        // como antes: mantém o que estava na tela e só encerra o "carregando"
        if (!stale.signal.aborted) setLoaded(prev => ({ page: query.page, products: [], totalPages: 0, ...prev, key }));
      });
    return () => stale.abort();
  }, [key, query.page, query.search]);

  const products = loaded?.products ?? [];
  const page = loaded?.page ?? 0;
  const totalPages = loaded?.totalPages ?? 0;
  const goTo = (p: number) => setQuery(q => ({ ...q, page: p }));

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    // mesma busca de novo não recarrega (igual ao comportamento anterior)
    setQuery(q => (q.search === inputValue ? q : { page: 0, search: inputValue }));
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900">Nossos Produtos</h1>
        <p className="text-gray-500 mt-1">Encontre o que você precisa</p>
      </div>

      {/* Search */}
      <form onSubmit={handleSearch} className="flex gap-2 mb-8 max-w-md">
        <div className="relative flex-1">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text"
            placeholder="Buscar produtos..."
            value={inputValue}
            onChange={e => setInputValue(e.target.value)}
            className="w-full pl-9 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>
        <button
          type="submit"
          className="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-medium transition-colors"
        >
          Buscar
        </button>
      </form>

      {/* Grid */}
      {loading ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="bg-gray-100 rounded-xl aspect-[3/4] animate-pulse" />
          ))}
        </div>
      ) : products.length === 0 ? (
        <div className="text-center py-20 text-gray-400">
          <Search size={48} className="mx-auto mb-3 opacity-30" />
          <p className="text-lg">Nenhum produto encontrado</p>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
            {products.map(p => <ProductCard key={p.id} product={p} />)}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-3 mt-10">
              <button
                onClick={() => goTo(page - 1)}
                disabled={page === 0}
                className="p-2 rounded-lg border border-gray-300 text-gray-500 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <ChevronLeft size={18} />
              </button>
              <span className="text-sm text-gray-600">
                Página <strong>{page + 1}</strong> de <strong>{totalPages}</strong>
              </span>
              <button
                onClick={() => goTo(page + 1)}
                disabled={page >= totalPages - 1}
                className="p-2 rounded-lg border border-gray-300 text-gray-500 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <ChevronRight size={18} />
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
