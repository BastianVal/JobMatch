import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = (__ENV.K6_BASE_URL || 'http://localhost:8090').replace(/\/$/, '');
const cookieName = __ENV.K6_SESSION_COOKIE_NAME || 'jobmatch_session';
const sessionCookie = __ENV.K6_SESSION_COOKIE;

if (!sessionCookie) {
  throw new Error('Define K6_SESSION_COOKIE con una sesión autenticada de pruebas.');
}

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

export default function () {
  const response = http.get(`${baseUrl}/api/v1/jobs/search?limit=25`, {
    headers: { Cookie: `${cookieName}=${sessionCookie}` },
    tags: { endpoint: 'job-search' },
  });
  check(response, {
    'búsqueda responde 200': (result) => result.status === 200,
    'búsqueda entrega JSON': (result) => (result.headers['Content-Type'] || '').includes('application/json'),
  });
  sleep(1);
}
