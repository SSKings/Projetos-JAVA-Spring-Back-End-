import { useEffect, useState } from 'react';
import QRCode from 'qrcode';

/** QR Code em SVG (sem canvas). O conteúdo vem do back-end; aqui só é desenhado. */
export default function QrCodeImage({ value, alt, className = 'w-48 h-48' }: { value: string; alt: string; className?: string }) {
  const [src, setSrc] = useState('');
  useEffect(() => {
    let active = true;
    Promise.resolve()
      .then(() => QRCode.toString(value, { type: 'svg', margin: 1, errorCorrectionLevel: 'M' }))
      .then(svg => { if (active) setSrc(`data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`); })
      .catch(() => { if (active) setSrc(''); });
    return () => { active = false; };
  }, [value]);
  return src
    ? <img src={src} alt={alt} className={`${className} border border-gray-100 rounded-lg`} />
    : <div className={`${className} bg-gray-100 rounded-lg animate-pulse`} aria-label="Gerando QR Code" />;
}
