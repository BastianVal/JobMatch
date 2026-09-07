import { describe, expect, it } from 'vitest';
import { publicationLabel } from './publication';

const now = Date.UTC(2026, 7, 20, 12, 0, 0);

describe('publicationLabel', () => {
  it('uses whole-hour labels through eleven hours', () => {
    expect(publicationLabel(new Date(now - 1.9 * 3_600_000).toISOString(), now)).toBe('Hace 1 hora');
    expect(publicationLabel(new Date(now - 11.9 * 3_600_000).toISOString(), now)).toBe('Hace 11 horas');
  });
  it('uses whole-day labels from twelve hours through six days', () => {
    expect(publicationLabel(new Date(now - 12 * 3_600_000).toISOString(), now)).toBe('Hace 1 día');
    expect(publicationLabel(new Date(now - 6.9 * 86_400_000).toISOString(), now)).toBe('Hace 6 días');
  });
  it('uses an absolute Spanish date afterwards and keeps unknown dates blank', () => {
    expect(publicationLabel(new Date(Date.UTC(2026, 7, 11, 12)).toISOString(), now)).toBe('11 de Agosto');
    expect(publicationLabel(undefined, now)).toBe('');
    expect(publicationLabel('not-a-date', now)).toBe('');
  });
});
