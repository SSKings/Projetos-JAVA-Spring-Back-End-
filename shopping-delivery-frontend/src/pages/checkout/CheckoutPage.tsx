import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MapPin, ClipboardList, CheckCircle, Plus } from 'lucide-react';
import { getAddresses, createAddress, type AddressPayload } from '../../api/addresses';
import { checkout } from '../../api/orders';
import { useCart } from '../../contexts/CartContext';
import type { Address, Order } from '../../types';

type Step = 1 | 2 | 3;

const STEPS = [
  { step: 1, label: 'Endereço', icon: MapPin },
  { step: 2, label: 'Revisão', icon: ClipboardList },
  { step: 3, label: 'Confirmação', icon: CheckCircle },
];

const emptyAddress: AddressPayload = {
  zipCode: '', street: '', number: '', complement: '',
  neighborhood: '', city: '', state: '',
};

export default function CheckoutPage() {
  const navigate = useNavigate();
  const { cart, clearCart } = useCart();

  const [step, setStep] = useState<Step>(1);
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [showAddressForm, setShowAddressForm] = useState(false);
  const [newAddress, setNewAddress] = useState<AddressPayload>(emptyAddress);
  const [savingAddress, setSavingAddress] = useState(false);
  const [placing, setPlacing] = useState(false);
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (cart.items.length === 0 && step !== 3) navigate('/cart');
    getAddresses().then(data => {
      setAddresses(data);
      if (data.length > 0) setSelectedAddressId(data[0].id);
    });
  }, []); // eslint-disable-line

  const selectedAddress = addresses.find(a => a.id === selectedAddressId);

  const handleSaveAddress = async (e: React.FormEvent) => {
    e.preventDefault();
    setSavingAddress(true);
    try {
      const created = await createAddress(newAddress);
      setAddresses(prev => [...prev, created]);
      setSelectedAddressId(created.id);
      setShowAddressForm(false);
      setNewAddress(emptyAddress);
    } catch {
      setError('Erro ao salvar endereço.');
    } finally {
      setSavingAddress(false);
    }
  };

  const handlePlaceOrder = async () => {
    if (!selectedAddressId) return;
    setPlacing(true);
    setError('');
    try {
      const created = await checkout(selectedAddressId);
      setOrder(created);
      await clearCart();
      setStep(3);
    } catch {
      setError('Erro ao finalizar pedido. Tente novamente.');
    } finally {
      setPlacing(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      {/* Step indicator */}
      <div className="flex items-center justify-center mb-10">
        {STEPS.map(({ step: s, label, icon: Icon }, i) => (
          <div key={s} className="flex items-center">
            <div className={`flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium transition-colors ${
              step === s ? 'bg-indigo-600 text-white' :
              step > s ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-400'
            }`}>
              <Icon size={15} />
              <span className="hidden sm:inline">{label}</span>
            </div>
            {i < STEPS.length - 1 && (
              <div className={`w-8 h-0.5 mx-1 ${step > s ? 'bg-green-400' : 'bg-gray-200'}`} />
            )}
          </div>
        ))}
      </div>

      {/* Step 1: Address */}
      {step === 1 && (
        <div className="space-y-5">
          <h2 className="text-xl font-bold text-gray-900">Escolha o endereço de entrega</h2>

          {error && <p className="text-sm text-red-600 bg-red-50 px-4 py-2 rounded-lg">{error}</p>}

          {/* Address list */}
          <div className="space-y-3">
            {addresses.map(addr => (
              <label key={addr.id} className={`flex items-start gap-3 p-4 rounded-xl border-2 cursor-pointer transition-colors ${
                selectedAddressId === addr.id ? 'border-indigo-500 bg-indigo-50' : 'border-gray-200 bg-white hover:border-gray-300'
              }`}>
                <input
                  type="radio"
                  name="address"
                  checked={selectedAddressId === addr.id}
                  onChange={() => setSelectedAddressId(addr.id)}
                  className="mt-1 accent-indigo-600"
                />
                <div className="text-sm">
                  <p className="font-medium text-gray-900">
                    {addr.street}{addr.number ? `, ${addr.number}` : ''}
                    {addr.complement ? ` — ${addr.complement}` : ''}
                  </p>
                  <p className="text-gray-500 mt-0.5">
                    {addr.neighborhood} · {addr.city}/{addr.state} · CEP {addr.zipCode}
                  </p>
                </div>
              </label>
            ))}

            {/* Add new */}
            {!showAddressForm ? (
              <button
                onClick={() => setShowAddressForm(true)}
                className="w-full flex items-center justify-center gap-2 py-3 border-2 border-dashed border-gray-300 hover:border-indigo-400 rounded-xl text-sm text-gray-500 hover:text-indigo-600 transition-colors"
              >
                <Plus size={16} /> Adicionar novo endereço
              </button>
            ) : (
              <form onSubmit={handleSaveAddress} className="bg-white border border-gray-200 rounded-xl p-5 space-y-3">
                <h3 className="font-semibold text-gray-900 text-sm">Novo endereço</h3>
                <div className="grid grid-cols-2 gap-3">
                  {[
                    { key: 'zipCode', label: 'CEP', placeholder: '00000-000', col: 1 },
                    { key: 'street', label: 'Rua', placeholder: 'Rua das Flores', col: 2 },
                    { key: 'number', label: 'Número', placeholder: '123', col: 1 },
                    { key: 'complement', label: 'Complemento', placeholder: 'Apto 4', col: 1 },
                    { key: 'neighborhood', label: 'Bairro', placeholder: 'Centro', col: 1 },
                    { key: 'city', label: 'Cidade', placeholder: 'São Paulo', col: 1 },
                    { key: 'state', label: 'UF', placeholder: 'SP', col: 1 },
                  ].map(f => (
                    <div key={f.key} className={f.col === 2 ? 'col-span-2' : ''}>
                      <label className="text-xs font-medium text-gray-600 block mb-1">{f.label}</label>
                      <input
                        type="text"
                        value={newAddress[f.key as keyof AddressPayload] ?? ''}
                        onChange={e => setNewAddress(a => ({ ...a, [f.key]: e.target.value }))}
                        required={f.key !== 'complement'}
                        placeholder={f.placeholder}
                        maxLength={f.key === 'state' ? 2 : undefined}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                      />
                    </div>
                  ))}
                </div>
                <div className="flex gap-2 pt-1">
                  <button type="button" onClick={() => setShowAddressForm(false)}
                    className="flex-1 py-2 border border-gray-300 rounded-lg text-sm text-gray-600 hover:bg-gray-50">
                    Cancelar
                  </button>
                  <button type="submit" disabled={savingAddress}
                    className="flex-1 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-medium disabled:opacity-60">
                    {savingAddress ? 'Salvando...' : 'Salvar'}
                  </button>
                </div>
              </form>
            )}
          </div>

          <button
            onClick={() => setStep(2)}
            disabled={!selectedAddressId}
            className="w-full py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          >
            Continuar
          </button>
        </div>
      )}

      {/* Step 2: Review */}
      {step === 2 && (
        <div className="space-y-5">
          <h2 className="text-xl font-bold text-gray-900">Revisar pedido</h2>

          {error && <p className="text-sm text-red-600 bg-red-50 px-4 py-2 rounded-lg">{error}</p>}

          {/* Address */}
          <div className="bg-white border border-gray-200 rounded-xl p-4">
            <div className="flex items-center justify-between mb-2">
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">Entrega em</p>
              <button onClick={() => setStep(1)} className="text-xs text-indigo-600 hover:underline">Alterar</button>
            </div>
            {selectedAddress && (
              <p className="text-sm text-gray-700">
                {selectedAddress.street}{selectedAddress.number ? `, ${selectedAddress.number}` : ''} ·{' '}
                {selectedAddress.neighborhood} · {selectedAddress.city}/{selectedAddress.state}
              </p>
            )}
          </div>

          {/* Items */}
          <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
            <div className="px-4 py-3 border-b border-gray-100">
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">Itens do pedido</p>
            </div>
            {cart.items.map(item => (
              <div key={item.id} className="flex items-center justify-between px-4 py-3 border-b border-gray-50 last:border-0">
                <div className="flex items-center gap-3">
                  {item.productImageUrl && (
                    <img src={item.productImageUrl} alt={item.productName}
                      className="w-10 h-10 rounded-lg object-cover" />
                  )}
                  <div>
                    <p className="text-sm font-medium text-gray-900">{item.productName}</p>
                    <p className="text-xs text-gray-500">Qtd: {item.quantity}</p>
                  </div>
                </div>
                <span className="text-sm font-semibold">
                  {item.subtotal.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
                </span>
              </div>
            ))}
            <div className="px-4 py-3 bg-gray-50 flex justify-between font-bold">
              <span>Total</span>
              <span className="text-indigo-600">
                {cart.total.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
              </span>
            </div>
          </div>

          <button
            onClick={handlePlaceOrder}
            disabled={placing}
            className="w-full py-3 bg-green-600 hover:bg-green-700 text-white rounded-xl font-semibold transition-colors disabled:opacity-60"
          >
            {placing ? 'Processando...' : 'Confirmar Pedido'}
          </button>
        </div>
      )}

      {/* Step 3: Confirmation */}
      {step === 3 && order && (
        <div className="text-center py-10 space-y-4">
          <div className="w-20 h-20 bg-green-100 rounded-full flex items-center justify-center mx-auto">
            <CheckCircle size={40} className="text-green-600" />
          </div>
          <h2 className="text-2xl font-bold text-gray-900">Pedido confirmado!</h2>
          <p className="text-gray-500">
            Pedido <span className="font-semibold text-indigo-600">#{order.id}</span> criado com sucesso.
          </p>
          <p className="text-gray-500 text-sm">
            Total:{' '}
            <span className="font-semibold">
              {order.total.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
            </span>
          </p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center pt-4">
            <button
              onClick={() => navigate('/orders')}
              className="px-6 py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold transition-colors"
            >
              Ver Meus Pedidos
            </button>
            <button
              onClick={() => navigate('/')}
              className="px-6 py-3 border border-gray-300 text-gray-700 hover:bg-gray-50 rounded-xl font-semibold transition-colors"
            >
              Continuar Comprando
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
