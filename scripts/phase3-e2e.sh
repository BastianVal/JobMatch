#!/bin/sh
set -eu

base_url="${BASE_URL:-http://caddy}"
mailpit_url="${MAILPIT_URL:-http://mailpit:8025}"
host_header="${APP_HOST:-localhost}"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
cookies="$work_dir/cookies.txt"
body="$work_dir/body.json"
first_page="$work_dir/first-page.json"
email="phase3-$(date +%s)@example.com"
password='correct-horse-battery'

csrf_json="$(curl -fsS -H "Host: $host_header" -c "$cookies" "$base_url/api/v1/auth/csrf")"
csrf="$(printf '%s' "$csrf_json" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -c "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "{\"email\":\"$email\",\"password\":\"$password\"}" "$base_url/api/v1/auth/register")"
test "$status" = '202'

message_id=''
attempt=0
while test "$attempt" -lt 15; do
  messages="$(curl -fsS "$mailpit_url/api/v1/messages?limit=1")"
  message_id="$(printf '%s' "$messages" | grep -o '"ID":"[^"]*"' | head -1 | cut -d '"' -f 4)"
  if test -n "$message_id"; then
    message="$(curl -fsS "$mailpit_url/api/v1/message/$message_id")"
    printf '%s' "$message" | grep -q "$email" && break
  fi
  message_id=''
  attempt=$((attempt + 1))
  sleep 1
done
test -n "$message_id"
token="$(printf '%s' "$message" | grep -o 'token=[A-Za-z0-9_.-]*' | head -1 | cut -d= -f2)"
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -c "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "{\"token\":\"$token\"}" "$base_url/api/v1/auth/verify-email")"
test "$status" = '204'
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -c "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "{\"email\":\"$email\",\"password\":\"$password\"}" "$base_url/api/v1/auth/login")"
test "$status" = '204'

status="$(curl -sS -H "Host: $host_header" -o "$first_page" -w '%{http_code}' -b "$cookies" \
  "$base_url/api/v1/jobs/search?q=Java&roleFamilyId=11000000-0000-0000-0000-000000000001&limit=5")"
test "$status" = '200'
test "$(grep -o '"title":"[^"]*"' "$first_page" | wc -l)" = '5'
grep -q 'Desarrollador Backend Java' "$first_page"
cursor="$(grep -o '"nextCursor":"[^"]*"' "$first_page" | cut -d '"' -f 4)"
first_id="$(grep -o '"id":"[^"]*"' "$first_page" | head -1 | cut -d '"' -f 4)"
test -n "$cursor"
test -n "$first_id"

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" \
  "$base_url/api/v1/jobs/search?q=Java&roleFamilyId=11000000-0000-0000-0000-000000000001&limit=5&cursor=$cursor")"
test "$status" = '200'
if grep -q "$first_id" "$body"; then echo 'cursor repeated the first result' >&2; exit 1; fi

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" \
  "$base_url/api/v1/jobs/$first_id")"
test "$status" = '200'
grep -q '"sourceLinks"' "$body"
grep -q '"requirements"' "$body"
grep -q '"skillRequirements"' "$body"

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X PUT \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" -H 'If-Match: "0"' \
  --data '{"excludedEmployers":["Empresa Fixture 001"]}' "$base_url/api/v1/me/profile")"
test "$status" = '200'
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" \
  "$base_url/api/v1/jobs/search?q=Java&excludeEmployers=true&limit=25")"
test "$status" = '200'
if grep -q 'Empresa Fixture 001' "$body"; then echo 'excluded employer was returned' >&2; exit 1; fi

saved_payload='{"name":"Backend remoto","criteria":{"query":"Java","roleFamilyIds":["11000000-0000-0000-0000-000000000001"],"remoteModes":["REMOTE"],"countryCode":"MX"}}'
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "$saved_payload" "$base_url/api/v1/me/saved-searches")"
test "$status" = '201'
grep -q '"version":1' "$body"
saved_id="$(grep -o '"id":"[^"]*"' "$body" | head -1 | cut -d '"' -f 4)"
test -n "$saved_id"

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" \
  "$base_url/api/v1/me/saved-searches")"
test "$status" = '200'
grep -q "$saved_id" "$body"

updated_payload='{"name":"Backend híbrido","criteria":{"query":"Spring Boot","remoteModes":["HYBRID"],"countryCode":"MX"}}'
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X PUT \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" -H 'If-Match: "1"' \
  --data "$updated_payload" "$base_url/api/v1/me/saved-searches/$saved_id")"
test "$status" = '200'
grep -q '"version":2' "$body"

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X DELETE \
  -H "X-CSRF-TOKEN: $csrf" -H 'If-Match: "1"' "$base_url/api/v1/me/saved-searches/$saved_id")"
test "$status" = '409'
grep -q 'VERSION_CONFLICT' "$body"
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X DELETE \
  -H "X-CSRF-TOKEN: $csrf" -H 'If-Match: "2"' "$base_url/api/v1/me/saved-searches/$saved_id")"
test "$status" = '204'

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" \
  "$base_url/api/v1/jobs/search?cursor=invalid")"
test "$status" = '400'
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" \
  "$base_url/api/v1/jobs/search?roleFamilyId=not-a-uuid")"
test "$status" = '400'
grep -q 'VALIDATION_FAILED' "$body"

# Deja una búsqueda personal para comprobar que la eliminación de cuenta la limpia.
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data '{"name":"Temporal","criteria":{"query":"Java"}}' "$base_url/api/v1/me/saved-searches")"
test "$status" = '201'

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X DELETE \
  -H "X-CSRF-TOKEN: $csrf" "$base_url/api/v1/me/account")"
test "$status" = '204'

printf 'phase3-e2e: OK (search, filters, cursor, detail, exclusions, saved searches)\n'
