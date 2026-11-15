/** Porta padrão da API Spring Boot (server.port em application.yml). */
export const DEFAULT_API_PORT = 8080;

/**
 * Define a URL da API.
 *
 * - `VITE_API_BASE_URL=/` → mesma origem (produção atrás do nginx, que faz proxy de /api): URLs relativas.
 * - Se `VITE_API_BASE_URL` estiver definida, usa ela (API em outro domínio).
 * - Senão, usa o **mesmo host pelo qual a página foi aberta**, na porta 8080. Assim, quem acessa
 *   `http://192.168.1.180:5173` chama `http://192.168.1.180:8080` — com `localhost` fixo, o navegador de
 *   outro computador tentaria falar com a própria máquina e mostraria "Não foi possível conectar ao servidor".
 */
export function resolveApiBaseUrl(
  envUrl: string | undefined,
  location: Pick<Location, 'protocol' | 'hostname'>,
): string {
  if (envUrl?.trim() === '/') return '';
  const configured = envUrl?.trim().replace(/\/+$/, '');
  if (configured) return configured;
  return `${location.protocol}//${location.hostname}:${DEFAULT_API_PORT}`;
}
