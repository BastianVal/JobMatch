#!/usr/bin/env sh
set -eu

backup_dir="${BACKUP_DIR:-/srv/jobmatch/backups}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_file="$backup_dir/jobmatch-$timestamp.dump"

case "$backup_dir" in
  /|"") echo "BACKUP_DIR debe ser un directorio específico." >&2; exit 2 ;;
esac

mkdir -p "$backup_dir"
umask 077

docker compose exec -T postgres sh -ec 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup_file"
test -s "$backup_file"
sha256sum "$backup_file" > "$backup_file.sha256"
printf 'Respaldo creado y verificado: %s\n' "$backup_file"
