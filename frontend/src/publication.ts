const MONTHS = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];

/** Formats a publication date into the deliberately small set of labels used in discovery cards. */
export function publicationLabel(value?: string, now = Date.now()): string {
  if (!value) return '';
  const published = new Date(value);
  if (Number.isNaN(published.valueOf())) return '';
  const elapsed = now - published.valueOf();
  if (elapsed < 0) return '';
  const hours = Math.floor(elapsed / 3_600_000);
  if (hours < 12) {
    const rounded = Math.max(1, hours);
    return `Hace ${rounded} ${rounded === 1 ? 'hora' : 'horas'}`;
  }
  const days = Math.floor(elapsed / 86_400_000);
  if (days < 7) {
    const rounded = Math.max(1, days);
    return `Hace ${rounded} ${rounded === 1 ? 'día' : 'días'}`;
  }
  return `${published.getDate()} de ${MONTHS[published.getMonth()]}`;
}
