#!/usr/bin/env sh
set -eu

backup_file="${1:-}"
confirmation="${2:-}"

if [ -z "$backup_file" ] || [ ! -f "$backup_file" ]; then
  echo "Uso: $0 /ruta/al/respaldo.dump --confirm" >&2
  exit 2
fi
if [ "$confirmation" != "--confirm" ]; then
  echo "La restauración reemplaza datos. Vuelve a ejecutar con --confirm." >&2
  exit 2
fi
if [ ! -f "$backup_file.sha256" ]; then
  echo "Falta el archivo de checksum: $backup_file.sha256" >&2
  exit 2
fi

(cd "$(dirname "$backup_file")" && sha256sum -c "$(basename "$backup_file").sha256")
docker compose exec -T postgres sh -ec 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner' < "$backup_file"
printf 'Restauración terminada. Ejecuta las comprobaciones del runbook antes de reabrir tráfico.\n'
