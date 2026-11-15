import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import QRCode from 'qrcode';
import PixPayment from './PixPayment';
import * as ordersApi from '../api/orders';
import * as clipboard from '../utils/clipboard';
import { PIX_CODE, payment } from '../test/fixtures';

vi.mock('../api/orders');
vi.mock('../utils/clipboard');

beforeEach(() => vi.resetAllMocks());
afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('PixPayment', () => {
  it('mostra QR Code do BR Code, valor, contador, instruções e o copia e cola', async () => {
    render(<PixPayment orderId={3} payment={payment({ expiresInSeconds: 125 })} />);

    const img = await screen.findByRole('img', { name: 'QR Code Pix' });
    expect(img.getAttribute('src')).toMatch(/^data:image\/svg\+xml/);
    expect(decodeURIComponent(img.getAttribute('src')!)).toContain('<svg');
    expect(screen.getByText(/R\$\s?59,80/)).toBeInTheDocument();
    expect(screen.getByText('02:05')).toBeInTheDocument();
    expect(screen.getByLabelText('Código Pix copia e cola')).toHaveValue(PIX_CODE);
    expect(screen.getByText(/Pix Copia e Cola/)).toBeInTheDocument();
    expect(screen.getByText(/Identificador: TX0000/)).toBeInTheDocument();
  });

  it('o QR Code codifica exatamente o copia e cola', async () => {
    const spy = vi.spyOn(QRCode, 'toString');
    render(<PixPayment orderId={3} payment={payment()} />);
    await screen.findByRole('img', { name: 'QR Code Pix' });
    expect(spy).toHaveBeenCalledWith(PIX_CODE, expect.objectContaining({ type: 'svg' }));
  });

  it('falha ao gerar o QR mantém o copia e cola disponível', async () => {
    const spy = vi.spyOn(QRCode, 'toString').mockRejectedValueOnce(new Error('x') as never);
    render(<PixPayment orderId={3} payment={payment()} />);
    await waitFor(() => expect(spy).toHaveBeenCalled());
    expect(screen.queryByRole('img', { name: 'QR Code Pix' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('Código Pix copia e cola')).toHaveValue(PIX_CODE);
  });

  it('copia o código e mostra confirmação', async () => {
    vi.mocked(clipboard.copyText).mockResolvedValue(true);
    render(<PixPayment orderId={3} payment={payment()} />);

    await userEvent.click(screen.getByRole('button', { name: /Copiar código Pix/ }));
    expect(clipboard.copyText).toHaveBeenCalledWith(PIX_CODE);
    expect(screen.getByRole('button', { name: /Código copiado!/ })).toBeInTheDocument();
  });

  it('sem permissão para copiar, seleciona o texto e orienta a copiar manualmente', async () => {
    vi.mocked(clipboard.copyText).mockResolvedValue(false);
    render(<PixPayment orderId={3} payment={payment()} />);

    await userEvent.click(screen.getByRole('button', { name: /Copiar código Pix/ }));
    expect(screen.getByRole('alert')).toHaveTextContent(/copiar automaticamente/);
    const textarea = screen.getByLabelText('Código Pix copia e cola') as HTMLTextAreaElement;
    expect(textarea.selectionEnd - textarea.selectionStart).toBe(PIX_CODE.length);
  });

  it('consulta o status e avisa a tela pai quando o Pix é pago', async () => {
    const onStatusChange = vi.fn();
    vi.mocked(ordersApi.getPayment)
      .mockRejectedValueOnce(new Error('Network Error')) // falha momentânea é ignorada
      .mockResolvedValueOnce(payment())
      .mockResolvedValue(payment({ status: 'PAID', pixCopyPaste: null, expiresInSeconds: 0, paidAt: '2026-03-15T12:05:00' }));
    render(<PixPayment orderId={3} payment={payment()} onStatusChange={onStatusChange} pollIntervalMs={10} />);

    expect(await screen.findByText(/Pagamento confirmado!/)).toBeInTheDocument();
    expect(ordersApi.getPayment).toHaveBeenCalledWith(3);
    expect(onStatusChange).toHaveBeenCalledTimes(1);
    expect(onStatusChange.mock.calls[0][0].status).toBe('PAID');
    expect(screen.queryByLabelText('Código Pix copia e cola')).not.toBeInTheDocument();

    const calls = vi.mocked(ordersApi.getPayment).mock.calls.length;
    expect(calls).toBe(3); // erro → pendente → pago: uma consulta por vez
    await new Promise(r => setTimeout(r, 50));
    expect(vi.mocked(ordersApi.getPayment).mock.calls.length).toBe(calls); // parou de consultar
  });

  it('não sobrepõe consultas: a próxima só sai depois da resposta anterior (rede lenta)', async () => {
    let resolve!: (p: ReturnType<typeof payment>) => void;
    vi.mocked(ordersApi.getPayment).mockImplementation(() => new Promise(r => { resolve = r; }));
    const onStatusChange = vi.fn();
    render(<PixPayment orderId={3} payment={payment()} onStatusChange={onStatusChange} pollIntervalMs={5} />);

    await waitFor(() => expect(ordersApi.getPayment).toHaveBeenCalledTimes(1));
    await new Promise(r => setTimeout(r, 40)); // vários intervalos se passam com a 1ª consulta pendente
    expect(ordersApi.getPayment).toHaveBeenCalledTimes(1);

    await act(async () => { resolve(payment({ status: 'PAID', pixCopyPaste: null, expiresInSeconds: 0 })); });
    expect(onStatusChange).toHaveBeenCalledTimes(1);
  });

  it('resposta que chega depois de desmontar é ignorada', async () => {
    let resolve!: (p: ReturnType<typeof payment>) => void;
    vi.mocked(ordersApi.getPayment).mockImplementation(() => new Promise(r => { resolve = r; }));
    const onStatusChange = vi.fn();
    const { unmount } = render(<PixPayment orderId={3} payment={payment()} onStatusChange={onStatusChange} pollIntervalMs={5} />);
    await waitFor(() => expect(ordersApi.getPayment).toHaveBeenCalled());
    unmount();
    await act(async () => { resolve(payment({ status: 'PAID' })); });
    expect(onStatusChange).not.toHaveBeenCalled();
  });

  it('contador regressivo e aviso quando o prazo acaba', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: false });
    vi.mocked(ordersApi.getPayment).mockReturnValue(new Promise(() => {})); // consulta ainda sem resposta
    render(<PixPayment orderId={3} payment={payment({ expiresInSeconds: 61 })} pollIntervalMs={120_000} />);
    expect(screen.getByText('01:01').closest('p')).toHaveClass('text-gray-600');

    act(() => { vi.advanceTimersByTime(2000); });
    expect(screen.getByText('00:59').closest('p')).toHaveClass('text-red-600');

    act(() => { vi.advanceTimersByTime(60_000); });
    expect(screen.getByText('Prazo encerrado, verificando…')).toBeInTheDocument();
  });

  it.each([
    ['EXPIRED', /prazo para pagamento acabou/],
    ['CANCELLED', /pedido foi cancelado; não faça o pagamento/],
    ['REFUND_PENDING', /fará a devolução/],
  ] as const)('status %s mostra a mensagem certa e não oferece pagamento', (status, message) => {
    render(<PixPayment orderId={3} payment={payment({ status, pixCopyPaste: null, expiresInSeconds: 0 })} />);
    expect(screen.getByText(message)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Copiar/ })).not.toBeInTheDocument();
    expect(ordersApi.getPayment).not.toHaveBeenCalled();
  });
});
