export type ApiProblem = {
  status: number;
  code?: string;
  detail?: string;
  retryable?: boolean;
  fieldErrors?: Record<string, string>;
};

export class ApiError extends Error {
  constructor(public readonly problem: ApiProblem) {
    super(problem.detail || 'No fue posible completar la solicitud.');
  }
}

let csrf: { headerName: string; token: string } | undefined;

async function csrfHeaders(): Promise<Record<string, string>> {
  if (!csrf) {
    const response = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' });
    if (!response.ok) throw new ApiError({ status: response.status });
    csrf = await response.json();
  }
  const current = csrf;
  if (!current) throw new ApiError({ status: 500, detail: 'No se recibió el token de seguridad.' });
  return { [current.headerName]: current.token };
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method || 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    Object.entries(await csrfHeaders()).forEach(([key, value]) => headers.set(key, value));
  }
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const response = await fetch(`/api/v1${path}`, { ...init, headers, credentials: 'same-origin' });
  if (!response.ok) {
    let problem: ApiProblem = { status: response.status };
    try { problem = { ...problem, ...(await response.json()) }; } catch { /* respuesta sin cuerpo */ }
    if (response.status === 403) csrf = undefined;
    throw new ApiError(problem);
  }
  if (response.status === 204 || response.headers.get('content-length') === '0') return undefined as T;
  return response.json() as Promise<T>;
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.problem.code === 'VERSION_CONFLICT') return 'El dato cambió en otra sesión. Recargamos la versión vigente; intenta de nuevo.';
    return error.problem.detail || 'No fue posible completar la solicitud.';
  }
  return 'Ocurrió un error inesperado. Puedes volver a intentar.';
}

export function resetCsrf() { csrf = undefined; }
