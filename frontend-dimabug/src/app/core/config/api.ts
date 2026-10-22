import { environment } from '../../../environments/environment';

/**
 * Host del backend. En `ng serve` las peticiones usan `/api` (proxy.conf.json)
 * para evitar CORS; el proxy apunta a `environment.apiUrl`.
 */
export function apiUrl(path: string): string {
  const suffix = path.startsWith('/') ? path : `/${path}`;
  const host = (environment.apiUrl || '').replace(/\/$/, '');

  if (!environment.production) {
    return `/api${suffix}`;
  }

  if (!host || host === '/api') {
    return `/api${suffix}`;
  }

  return `${host}/api${suffix}`;
}

/** Solo el origen y la ruta base configurados del backend pueden recibir JWT. */
export function backendApiPath(value: string): string | null {
  try {
    if (!value || value !== value.trim() || value.includes('\\')) return null;
    const origin = globalThis.location.origin;
    const base = new URL(apiUrl('/'), origin);
    const url = new URL(value, origin);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password
        || url.origin !== base.origin) return null;
    const prefix = base.pathname.replace(/\/$/, '');
    if (url.pathname === prefix) return '/';
    return url.pathname.startsWith(prefix + '/') ? url.pathname.slice(prefix.length) : null;
  } catch {
    return null;
  }
}

export const PUBLIC_API_PATHS = ['/api/auth/login', '/api/health'] as const;

export function isPublicApiUrl(url: string): boolean {
  const path = backendApiPath(url);
  return path === '/auth/login' || path === '/health';
}
