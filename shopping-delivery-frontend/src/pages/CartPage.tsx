import { Link, useNavigate } from 'react-router-dom';
import { Trash2, Plus, Minus, ShoppingBag, ArrowRight } from 'lucide-react';
import { useCart } from '../contexts/CartContext';
import { LIMITS } from '../utils/validation';

export default function CartPage() {
  const { cart, updateItem, removeItem, loading } = useCart();
  const navigate = useNavigate();

  if (loading) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-10 text-center text-gray-400">
        Carregando carrinho...
      </div>
    );
  }

  if (cart.items.length === 0) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-20 text-center">
        <ShoppingBag size={64} className="mx-auto text-gray-200 mb-4" />
        <h2 className="text-xl font-bold text-gray-700">Seu carrinho está vazio</h2>
        <p className="text-gray-400 mt-2">Adicione produtos para continuar</p>
        <Link
          to="/"
          className="inline-block mt-6 px-6 py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold transition-colors"
        >
          Explorar Produtos
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Meu Carrinho</h1>

      <div className="grid lg:grid-cols-3 gap-6">
        {/* Items */}
        <div className="lg:col-span-2 space-y-3">
          {cart.items.map(item => (
            <div key={item.id} className="bg-white rounded-xl border border-gray-200 p-4 flex gap-4">
              {/* Image */}
              <div className="w-20 h-20 bg-gray-50 rounded-lg overflow-hidden flex-shrink-0">
                {item.productImageUrl ? (
                  <img src={item.productImageUrl} alt={item.productName}
                    className="w-full h-full object-cover" />
                ) : (
                  <div className="w-full h-full flex items-center justify-center text-gray-200">
                    <ShoppingBag size={24} />
                  </div>
                )}
              </div>

              {/* Info */}
              <div className="flex-1 min-w-0">
                <p className="font-medium text-gray-900 text-sm truncate">{item.productName}</p>
                <p className="text-indigo-600 font-bold mt-1">
                  {item.unitPrice.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
                </p>

                <div className="flex items-center justify-between mt-2">
                  {/* Qty controls */}
                  <div className="flex items-center border border-gray-200 rounded-lg overflow-hidden">
                    <button
                      onClick={() => updateItem(item.id, item.quantity - 1)}
                      disabled={item.quantity <= 1}
                      className="px-2.5 py-1.5 text-gray-600 hover:bg-gray-100 disabled:opacity-30"
                    >
                      <Minus size={12} />
                    </button>
                    <span className="px-3 py-1.5 text-sm font-medium min-w-[32px] text-center">
                      {item.quantity}
                    </span>
                    <button
                      onClick={() => updateItem(item.id, item.quantity + 1)}
                      disabled={item.quantity >= LIMITS.unitsPerItem}
                      title={item.quantity >= LIMITS.unitsPerItem ? `Limite de ${LIMITS.unitsPerItem} unidades por produto` : undefined}
                      className="px-2.5 py-1.5 text-gray-600 hover:bg-gray-100 disabled:opacity-30"
                    >
                      <Plus size={12} />
                    </button>
                  </div>

                  <div className="flex items-center gap-3">
                    <span className="text-sm font-semibold text-gray-700">
                      {item.subtotal.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
                    </span>
                    <button
                      onClick={() => removeItem(item.id)}
                      className="text-gray-400 hover:text-red-500 transition-colors"
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Summary */}
        <div className="lg:col-span-1">
          <div className="bg-white rounded-xl border border-gray-200 p-6 sticky top-24">
            <h2 className="font-semibold text-gray-900 mb-4">Resumo do Pedido</h2>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between text-gray-600">
                <span>{cart.itemCount} {cart.itemCount === 1 ? 'item' : 'itens'}</span>
                <span>{cart.total.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}</span>
              </div>
              <div className="flex justify-between text-gray-600">
                <span>Frete</span>
                <span className="text-green-600 font-medium">Grátis</span>
              </div>
            </div>
            <div className="border-t border-gray-100 mt-4 pt-4 flex justify-between font-bold text-gray-900">
              <span>Total</span>
              <span className="text-indigo-600 text-lg">
                {cart.total.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
              </span>
            </div>
            <button
              onClick={() => navigate('/checkout')}
              className="w-full mt-5 py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold flex items-center justify-center gap-2 transition-colors"
            >
              Finalizar Compra <ArrowRight size={18} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
