#!/bin/sh
set -eu

base_url="${BASE_URL:-http://localhost:8090}"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
headers="$tmp/headers"

curl -fsS -D "$headers" -o /dev/null "$base_url/"
grep -qi "^Content-Security-Policy: default-src 'self'; frame-ancestors 'none'" "$headers"
grep -qi '^X-Content-Type-Options: nosniff' "$headers"
grep -qi '^Referrer-Policy: no-referrer' "$headers"

status="$(curl -sS -D "$headers" -o /dev/null -w '%{http_code}' -X OPTIONS \
  -H 'Origin: https://attacker.example' -H 'Access-Control-Request-Method: POST' \
  "$base_url/api/v1/me/account")"
test "$status" = 401 || test "$status" = 403
if grep -qi '^Access-Control-Allow-Origin:' "$headers"; then
  echo 'CORS permitió un origen no autorizado.' >&2
  exit 1
fi

status="$(curl -sS -D "$headers" -o /dev/null -w '%{http_code}' "$base_url/actuator/prometheus")"
test "$status" = 404

printf 'phase8-security-e2e: OK (headers, CORS, actuator privado)\n'
