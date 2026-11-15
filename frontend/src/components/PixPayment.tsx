import { useEffect, useRef, useState } from 'react';
import QRCode from 'qrcode';
import { CheckCircle, Clock, Copy, QrCode, XCircle } from 'lucide-react';
import { getPayment } from '../api/orders';
import { copyText } from '../utils/clipboard';
import { formatBRL } from '../utils/format';
import type { Payment } from '../types';

interface Props {
  orderId: number;
  payment: Payment;
  /** Chamado quando o status muda (ex.: PENDING → PAID) — a tela pai avança a etapa/recarrega o pedido. */
  onStatusChange?: (payment: Payment) => void;
  pollIntervalMs?: number;
}

const formatRemaining = (seconds: number) =>
  `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;

/**
 * QR Code + "Pix copia e cola" com o valor do pedido. Enquanto pendente, consulta o back-end a cada
 * `pollIntervalMs` (o webhook do PSP ou o admin confirmam o pagamento do outro lado).
 */
export default function PixPayment({ orderId, payment: initial, onStatusChange, pollIntervalMs = 5000 }: Props) {
  const [payment, setPayment] = useState(initial);
  const [qrSrc, setQrSrc] = useState('');
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'manual'>('idle');
  const [deadline, setDeadline] = useState(() => Date.now() + initial.expiresInSeconds * 1000);
  const [now, setNow] = useState(() => Date.now());
  const textRef = useRef<HTMLTextAreaElement>(null);
  const onChangeRef = useRef(onStatusChange);
  useEffect(() => { onChangeRef.current = onStatusChange; }, [onStatusChange]);

  const pending = payment.status === 'PENDING';
  const code = payment.pixCopyPaste;

  // QR Code em SVG (não depende de canvas)
  useEffect(() => {
    if (!code) return;
    let active = true;
    Promise.resolve()
      .then(() => QRCode.toString(code, { type: 'svg', margin: 1, errorCorrectionLevel: 'M' }))
      .then(svg => { if (active) setQrSrc(`data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`); })
      .catch(() => { if (active) setQrSrc(''); });
    return () => { active = false; };
  }, [code]);

  // Polling do status enquanto pendente. Encadeado com setTimeout: a próxima consulta só sai depois que a
  // anterior respondeu (sem requisições sobrepostas em rede lenta) e respostas tardias são ignoradas.
  useEffect(() => {
    if (!pending) return;
    const stop = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    const schedule = () => {
      timer = setTimeout(() => {
        getPayment(orderId)
          .then(next => {
            if (stop.signal.aborted) return;
            setPayment(next);
            setDeadline(Date.now() + next.expiresInSeconds * 1000);
            if (next.status !== 'PENDING') onChangeRef.current?.(next);
            else schedule();
          })
          .catch(() => { if (!stop.signal.aborted) schedule(); }); // falha momentânea de rede: tenta de novo
      }, pollIntervalMs);
    };
    schedule();
    return () => { stop.abort(); clearTimeout(timer); };
  }, [pending, orderId, pollIntervalMs]);

  // Contador regressivo
  useEffect(() => {
    if (!pending) return;
    const tick = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(tick);
  }, [pending]);

  const handleCopy = async () => {
    if (!code) return;
    const ok = await copyText(code);
    setCopyState(ok ? 'copied' : 'manual');
    if (!ok) textRef.current?.select();
    else setTimeout(() => setCopyState('idle'), 3000);
  };

  const remaining = Math.max(0, Math.ceil((deadline - now) / 1000));

  return (
    <section aria-labelledby="pix-title" className="bg-white border border-gray-200 rounded-xl p-5">
      <h3 id="pix-title" className="flex items-center gap-2 font-semibold text-gray-900">
        <QrCode size={18} className="text-indigo-600" /> Pagamento via Pix
      </h3>

      <div aria-live="polite">
        {payment.status === 'PAID' && (
          <p className="flex items-center gap-2 mt-4 bg-green-50 text-green-700 rounded-lg px-4 py-3 text-sm">
            <CheckCircle size={18} /> Pagamento confirmado! Seu pedido já está sendo preparado.
          </p>
        )}
        {payment.status === 'EXPIRED' && (
          <p className="flex items-center gap-2 mt-4 bg-gray-100 text-gray-700 rounded-lg px-4 py-3 text-sm">
            <XCircle size={18} /> O prazo para pagamento acabou. O pedido foi cancelado e os itens voltaram ao estoque.
          </p>
        )}
        {payment.status === 'CANCELLED' && (
          <p className="flex items-center gap-2 mt-4 bg-gray-100 text-gray-700 rounded-lg px-4 py-3 text-sm">
            <XCircle size={18} /> Este pedido foi cancelado; não faça o pagamento.
          </p>
        )}
        {payment.status === 'REFUND_PENDING' && (
          <p className="flex items-center gap-2 mt-4 bg-red-50 text-red-700 rounded-lg px-4 py-3 text-sm">
            <XCircle size={18} /> Recebemos seu Pix, mas o pedido estava cancelado. A loja fará a devolução do valor.
          </p>
        )}
      </div>

      {pending && code && (
        <div className="mt-4 grid sm:grid-cols-[auto,1fr] gap-5 items-start">
          <div className="mx-auto">
            {qrSrc
              ? <img src={qrSrc} alt="QR Code Pix" className="w-56 h-56 border border-gray-100 rounded-lg" />
              : <div className="w-56 h-56 bg-gray-100 rounded-lg animate-pulse" aria-label="Gerando QR Code" />}
          </div>

          <div className="space-y-3 text-sm">
            <div>
              <p className="text-gray-500">Valor a pagar</p>
              <p className="text-2xl font-bold text-gray-900">{formatBRL(payment.amount)}</p>
            </div>
            <p className={`flex items-center gap-1.5 ${remaining <= 60 ? 'text-red-600' : 'text-gray-600'}`}>
              <Clock size={15} />
              {remaining > 0 ? <>Expira em <strong>{formatRemaining(remaining)}</strong></> : 'Prazo encerrado, verificando…'}
            </p>

            <ol className="list-decimal list-inside text-gray-600 space-y-0.5">
              <li>Abra o app do seu banco e escolha <strong>Pix</strong>.</li>
              <li>Leia o QR Code ou use <strong>Pix Copia e Cola</strong>.</li>
              <li>Confira o valor e confirme. Esta tela atualiza sozinha.</li>
            </ol>

            <label htmlFor="pix-code" className="block text-gray-500">Código Pix copia e cola</label>
            <textarea
              id="pix-code"
              ref={textRef}
              readOnly
              rows={3}
              value={code}
              onFocus={e => e.currentTarget.select()}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-xs font-mono break-all bg-gray-50"
            />
            <button
              type="button"
              onClick={handleCopy}
              className="w-full flex items-center justify-center gap-2 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg font-medium"
            >
              <Copy size={16} /> {copyState === 'copied' ? 'Código copiado!' : 'Copiar código Pix'}
            </button>
            {copyState === 'manual' && (
              <p role="alert" className="text-xs text-amber-700">
                Não foi possível copiar automaticamente. O código está selecionado: use Ctrl+C (ou toque e segure).
              </p>
            )}
            <p className="text-xs text-gray-400">Identificador: {payment.txid}</p>
          </div>
        </div>
      )}
    </section>
  );
}
