import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, resetCsrf } from './api';

describe('frontend foundation', () => {
  it('uses the expected public API prefix', () => {
    expect('/api/v1').toMatch(/^\/api\/v1$/);
  });

  it('adds the session CSRF token to mutations', async () => {
    const request = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'safe-token' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', request);

    await api('/me/job-impressions', { method: 'POST', body: JSON.stringify({ jobIds: ['job'] }) });

    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/auth/csrf', { credentials: 'same-origin' });
    const mutation = request.mock.calls[1][1] as RequestInit;
    expect(new Headers(mutation.headers).get('X-CSRF-TOKEN')).toBe('safe-token');
    expect(mutation.credentials).toBe('same-origin');
  });

  it('keeps recoverable problem details from the API', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'VERSION_CONFLICT', detail: 'Cambió.' }), { status: 409 })));
    await expect(api('/me/account')).rejects.toMatchObject({
      problem: expect.objectContaining({ status: 409, code: 'VERSION_CONFLICT' })
    } satisfies Partial<ApiError>);
  });

  it('announces an expired session so the app can return to login', async () => {
    const expired = vi.fn();
    vi.stubGlobal('window', { dispatchEvent: expired });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'AUTHENTICATION_REQUIRED' }), { status: 401 })));

    await expect(api('/me/account')).rejects.toMatchObject({ problem: expect.objectContaining({ status: 401 }) });
    expect(expired).toHaveBeenCalledOnce();
  });
});

afterEach(() => { vi.unstubAllGlobals(); resetCsrf(); });
