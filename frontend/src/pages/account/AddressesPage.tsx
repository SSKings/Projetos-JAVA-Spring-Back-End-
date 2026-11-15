import { useEffect, useState } from 'react';
import { MapPin, Plus, Trash2 } from 'lucide-react';
import { createAddress, deleteAddress, getAddresses, type AddressPayload } from '../../api/addresses';
import AddressForm from '../../components/AddressForm';
import { getErrorMessage } from '../../utils/format';
import type { Address } from '../../types';

/** Endereços de entrega do cliente: listar, cadastrar e excluir (com confirmação). */
export default function AddressesPage() {
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [confirmingId, setConfirmingId] = useState<number | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    getAddresses()
      .then(setAddresses)
      .catch(err => setError(getErrorMessage(err, 'Não foi possível carregar seus endereços.')))
      .finally(() => setLoading(false));
  }, []);

  const handleCreate = async (payload: AddressPayload) => {
    const created = await createAddress(payload);
    setAddresses(prev => [...prev, created]);
    setShowForm(false);
  };

  const handleDelete = async (id: number) => {
    setError('');
    try {
      await deleteAddress(id);
      setAddresses(prev => prev.filter(a => a.id !== id));
    } catch (err) {
      setError(getErrorMessage(err, 'Não foi possível excluir o endereço.'));
    } finally {
      setConfirmingId(null);
    }
  };

  if (loading) return <div className="h-32 bg-gray-100 rounded-xl animate-pulse" aria-busy="true" />;

  return (
    <div className="space-y-3">
      {error && <p className="text-sm text-red-600 bg-red-50 px-4 py-2 rounded-lg">{error}</p>}

      {addresses.length === 0 && !showForm && (
        <div className="text-center py-10 text-gray-500">
          <MapPin size={48} className="mx-auto text-gray-200 mb-3" />
          Nenhum endereço cadastrado.
        </div>
      )}

      <ul className="space-y-3">
        {addresses.map(addr => (
          <li key={addr.id} className="flex items-start justify-between gap-3 bg-white rounded-xl border border-gray-200 p-4">
            <div className="text-sm">
              <p className="font-medium text-gray-900">
                {addr.street}{addr.number ? `, ${addr.number}` : ''}{addr.complement ? ` — ${addr.complement}` : ''}
              </p>
              <p className="text-gray-500 mt-0.5">
                {addr.neighborhood} · {addr.city}/{addr.state} · CEP {addr.zipCode}
              </p>
            </div>
            {confirmingId === addr.id ? (
              <div className="flex gap-2 shrink-0">
                <button onClick={() => setConfirmingId(null)} className="px-3 py-1.5 text-xs border border-gray-300 rounded-lg text-gray-600">
                  Manter
                </button>
                <button onClick={() => handleDelete(addr.id)} className="px-3 py-1.5 text-xs bg-red-600 hover:bg-red-700 text-white rounded-lg">
                  Confirmar exclusão
                </button>
              </div>
            ) : (
              <button
                onClick={() => setConfirmingId(addr.id)}
                aria-label={`Excluir endereço ${addr.street}, ${addr.number}`}
                className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg shrink-0"
              >
                <Trash2 size={16} />
              </button>
            )}
          </li>
        ))}
      </ul>

      {showForm ? (
        <AddressForm onSubmit={handleCreate} onCancel={() => setShowForm(false)} />
      ) : (
        <button
          onClick={() => setShowForm(true)}
          className="w-full flex items-center justify-center gap-2 py-3 border-2 border-dashed border-gray-300 hover:border-indigo-400 rounded-xl text-sm text-gray-500 hover:text-indigo-600 transition-colors"
        >
          <Plus size={16} /> Adicionar novo endereço
        </button>
      )}
    </div>
  );
}
