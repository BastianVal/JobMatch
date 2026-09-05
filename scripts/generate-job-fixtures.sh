#!/bin/sh
set -eu

count="${1:-10000}"
case "$count" in
  ''|*[!0-9]*) echo 'usage: scripts/generate-job-fixtures.sh [1..1000000]' >&2; exit 2 ;;
esac
if test "$count" -lt 1 || test "$count" -gt 1000000; then
  echo 'fixture count must be between 1 and 1000000' >&2
  exit 2
fi

docker compose exec -T postgres sh -c \
  'psql -v ON_ERROR_STOP=1 -v fixture_count="$1" -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  fixture-generator "$count" < scripts/generate-job-fixtures.sql

printf 'Generated or refreshed %s local job fixtures.\n' "$count"
