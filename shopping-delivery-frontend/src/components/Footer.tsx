import { Link } from 'react-router-dom';
import { company } from '../config/company';

/** Identificação do fornecedor em local de destaque (Decreto 7.962/2013, art. 2º). */
export default function Footer() {
  return (
    <footer className="border-t border-gray-200 bg-white mt-12">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 text-xs text-gray-500 flex flex-col sm:flex-row gap-4 justify-between">
        <address className="not-italic space-y-0.5">
          <p className="font-medium text-gray-700">{company.legalName} — CNPJ {company.cnpj}</p>
          <p>{company.address}</p>
          <p>Atendimento: {company.email} · {company.phone}</p>
        </address>
        <nav aria-label="Informações legais" className="flex flex-wrap gap-x-4 gap-y-1">
          <Link to="/termos" className="hover:text-indigo-600">Termos de uso</Link>
          <Link to="/privacidade" className="hover:text-indigo-600">Política de privacidade</Link>
          <Link to="/trocas-e-devolucoes" className="hover:text-indigo-600">Trocas e devoluções</Link>
        </nav>
      </div>
    </footer>
  );
}
