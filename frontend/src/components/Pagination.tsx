import { ChevronLeft, ChevronRight } from 'lucide-react';

interface Props {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}

export default function Pagination({ page, totalPages, onChange }: Props) {
  if (totalPages <= 1) return null;
  const btn = 'p-2 rounded-lg border border-gray-200 bg-white hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed';
  return (
    <div className="flex items-center justify-end gap-2 mt-4 text-sm text-gray-600">
      <button className={btn} disabled={page === 0} onClick={() => onChange(page - 1)} aria-label="Página anterior">
        <ChevronLeft size={16} />
      </button>
      <span>Página {page + 1} de {totalPages}</span>
      <button className={btn} disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)} aria-label="Próxima página">
        <ChevronRight size={16} />
      </button>
    </div>
  );
}
