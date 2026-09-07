#!/usr/bin/env sh
set -eu

work_dir="$(mktemp -d)"
restore_container="jobmatch-restore-verify-$$"
dump_file="$work_dir/jobmatch.dump"

cleanup() {
  docker rm -f "$restore_container" >/dev/null 2>&1 || true
  rm -rf "$work_dir"
}
trap cleanup EXIT INT TERM

docker compose exec -T postgres sh -ec 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$dump_file"
test -s "$dump_file"
sha256sum "$dump_file" | sha256sum -c -

docker run -d --rm --name "$restore_container" \
  -e POSTGRES_DB=jobmatch_restore_verify \
  -e POSTGRES_USER=jobmatch_restore_verify \
  -e POSTGRES_PASSWORD=temporary-restore-password \
  postgres:17-alpine >/dev/null

attempt=0
until docker exec "$restore_container" pg_isready -U jobmatch_restore_verify -d jobmatch_restore_verify >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 30 ]; then
    echo "La base temporal no quedó disponible." >&2
    exit 1
  fi
  sleep 1
done

docker exec -i "$restore_container" sh -ec \
  'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' < "$dump_file"
table_count="$(docker exec "$restore_container" psql -U jobmatch_restore_verify -d jobmatch_restore_verify -Atc \
  "SELECT count(*) FROM information_schema.tables WHERE table_schema IN ('iam','catalog','profile','jobs','ops','tracking')")"
test "$table_count" -gt 20
printf 'Respaldo y restauración verificados en una base temporal (%s tablas).\n' "$table_count"
