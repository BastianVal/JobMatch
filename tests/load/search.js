import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = (__ENV.K6_BASE_URL || 'http://localhost:8090').replace(/\/$/, '');
const cookieName = __ENV.K6_SESSION_COOKIE_NAME || 'jobmatch_session';
const sessionCookie = __ENV.K6_SESSION_COOKIE;
const email = __ENV.K6_EMAIL;
const password = __ENV.K6_PASSWORD;

export const options = {
  scenarios: {
    search: {
      executor: 'constant-vus',
      vus: Number(__ENV.K6_VUS || 50),
      duration: __ENV.K6_DURATION || '2m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export function setup() {
  if (sessionCookie) return { sessionCookie };
  if (!email || !password) {
    throw new Error('Define K6_SESSION_COOKIE o K6_EMAIL y K6_PASSWORD de una cuenta de pruebas verificada.');
  }

  const jar = http.cookieJar();
  const csrf = http.get(`${baseUrl}/api/v1/auth/csrf`);
  check(csrf, { 'CSRF disponible': (result) => result.status === 200 && Boolean(result.json('token')) });
  const login = http.post(`${baseUrl}/api/v1/auth/login`, JSON.stringify({ email, password }), {
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.json('token') },
  });
  check(login, { 'cuenta de carga autenticada': (result) => result.status === 204 });
  if (login.status !== 204) {
    throw new Error(`No se pudo iniciar sesión para la carga (HTTP ${login.status}). Usa una cuenta verificada y sus credenciales reales.`);
  }
  const session = login.cookies[cookieName]?.[0]?.value || jar.cookiesForURL(baseUrl)[cookieName]?.[0]?.value;
  if (!session) throw new Error(`El login no devolvió la cookie ${cookieName}.`);
  return { sessionCookie: session };
}

export default function ({ sessionCookie: authenticatedCookie }) {
  const response = http.get(`${baseUrl}/api/v1/jobs/search?limit=25`, {
    headers: { Cookie: `${cookieName}=${authenticatedCookie}` },
    tags: { endpoint: 'job-search' },
  });
  check(response, {
    'búsqueda responde 200': (result) => result.status === 200,
    'búsqueda entrega JSON': (result) => (result.headers['Content-Type'] || '').includes('application/json'),
  });
  sleep(1);
}
