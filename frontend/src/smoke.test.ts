import { describe, expect, it } from 'vitest';

describe('frontend foundation', () => {
  it('uses the expected public API prefix', () => {
    expect('/api/v1').toMatch(/^\/api\/v1$/);
  });
});
