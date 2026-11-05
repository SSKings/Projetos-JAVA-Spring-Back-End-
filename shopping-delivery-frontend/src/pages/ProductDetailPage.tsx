import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ShoppingCart, ArrowLeft, Package } from 'lucide-react';
import { getProduct } from '../api/products';
import { useAuth } from '../contexts/AuthContext';
import { useCart } from '../contexts/CartContext';
import type { Product } from '../types';

export default function ProductDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const { addItem } = useCart();
  const [product, setProduct] = useState<Product | null>(null);
  const [loading, setLoading] = useState(true);
  const [quantity, setQuantity] = useState(1);
  const [adding, setAdding] = useState(false);
  const [added, setAdded] = useState(false);

  useEffect(() => {
    if (!id) return;
    getProduct(Number(id))
      .then(setProduct)
      .catch(() => navigate('/'))
      .finally(() => setLoading(false));
  }, [id, navigate]);

  const handleAddToCart = async () => {
    if (!product) return;
    setAdding(true);
    try {
      await addItem(product.id, quantity);
      setAdded(true);
      setTimeout(() => setAdded(false), 2000);
    } finally {
      setAdding(false);
    }
  };

  if (loading) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-10">
        <div className="grid md:grid-cols-2 gap-10 animate-pulse">
          <div className="bg-gray-100 aspect-square rounded-2xl" />
          <div className="space-y-4">
            <div className="h-8 bg-gray-100 rounded w-3/4" />
            <div className="h-4 bg-gray-100 rounded w-1/4" />
            <div className="h-20 bg-gray-100 rounded" />
          </div>
        </div>
      </div>
    );
  }

  if (!product) return null;

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <Link to="/" className="inline-flex items-center gap-2 text-sm text-gray-500 hover:text-gray-700 mb-6">
        <ArrowLeft size={16} /> Voltar aos produtos
      </Link>

      <div className="grid md:grid-cols-2 gap-10">
        {/* Image */}
        <div className="bg-gray-50 rounded-2xl overflow-hidden aspect-square flex items-center justify-center">
          {product.imageUrl ? (
            <img src={product.imageUrl} alt={product.name} className="w-full h-full object-cover" />
          ) : (
            <Package size={80} className="text-gray-200" />
          )}
        </div>

        {/* Info */}
        <div className="flex flex-col">
          <h1 className="text-2xl font-bold text-gray-900">{product.name}</h1>
          <p className="text-3xl font-bold text-indigo-600 mt-3">
            {product.price.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
          </p>

          <p className="text-gray-600 mt-4 leading-relaxed">{product.description}</p>

          <div className="mt-4">
            {product.stockQuantity > 0 ? (
              <span className="text-sm text-green-600 font-medium bg-green-50 px-3 py-1 rounded-full">
                {product.stockQuantity} em estoque
              </span>
            ) : (
              <span className="text-sm text-red-600 font-medium bg-red-50 px-3 py-1 rounded-full">
                Sem estoque
              </span>
            )}
          </div>

          {isAuthenticated && product.stockQuantity > 0 && (
            <div className="mt-8 space-y-4">
              {/* Quantity */}
              <div className="flex items-center gap-3">
                <span className="text-sm font-medium text-gray-700">Quantidade:</span>
                <div className="flex items-center border border-gray-300 rounded-lg overflow-hidden">
                  <button
                    onClick={() => setQuantity(q => Math.max(1, q - 1))}
                    className="px-3 py-2 text-gray-600 hover:bg-gray-100 font-bold"
                  >−</button>
                  <span className="px-4 py-2 text-sm font-medium min-w-[40px] text-center">{quantity}</span>
                  <button
                    onClick={() => setQuantity(q => Math.min(product.stockQuantity, q + 1))}
                    className="px-3 py-2 text-gray-600 hover:bg-gray-100 font-bold"
                  >+</button>
                </div>
              </div>

              <button
                onClick={handleAddToCart}
                disabled={adding}
                className={`w-full flex items-center justify-center gap-2 py-3 rounded-xl font-semibold transition-colors ${
                  added
                    ? 'bg-green-600 text-white'
                    : 'bg-indigo-600 hover:bg-indigo-700 text-white'
                } disabled:opacity-60`}
              >
                <ShoppingCart size={18} />
                {added ? 'Adicionado!' : adding ? 'Adicionando...' : 'Adicionar ao Carrinho'}
              </button>
            </div>
          )}

          {!isAuthenticated && (
            <div className="mt-8 p-4 bg-indigo-50 rounded-xl text-sm text-indigo-700">
              <Link to="/login" className="font-semibold hover:underline">Faça login</Link> para adicionar ao carrinho
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
