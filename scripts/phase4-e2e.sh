#!/bin/sh
set -eu

base_url="${BASE_URL:-http://caddy}"
mailpit_url="${MAILPIT_URL:-http://mailpit:8025}"
host_header="${APP_HOST:-localhost}"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
cookies="$work_dir/cookies.txt"
body="$work_dir/body.json"
run_key="$(date +%s)"
email="phase4-$run_key@example.com"
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

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "{\"query\":\"Backend-$run_key\",\"location\":\"Ciudad de México\"}" "$base_url/api/v1/job-refreshes")"
test "$status" = '202'
grep -q '"status":"SCHEDULED"' "$body"
grep -q '"scheduledConnectors":5' "$body"
refresh_id="$(grep -o '"id":"[^"]*"' "$body" | head -1 | cut -d '"' -f 4)"
test -n "$refresh_id"

attempt=0
while test "$attempt" -lt 45; do
  curl -fsS -H "Host: $host_header" -b "$cookies" "$base_url/api/v1/job-refreshes/$refresh_id" > "$body"
  grep -q '"status":"COMPLETED"' "$body" && break
  attempt=$((attempt + 1))
  sleep 1
done
grep -q '"status":"COMPLETED"' "$body"
grep -q '"completedConnectors":5' "$body"
grep -q '"failedConnectors":0' "$body"

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" \
  "$base_url/api/v1/jobs/search?q=Tecnologia%20Ejemplo&limit=10")"
test "$status" = '200'
grep -q 'Desarrollador Backend Java' "$body"
job_id="$(grep -o '"id":"[^"]*"' "$body" | head -1 | cut -d '"' -f 4)"
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" "$base_url/api/v1/jobs/$job_id")"
test "$status" = '200'
test "$(grep -o '"source":"[^"]*"' "$body" | wc -l | tr -d ' ')" -ge '5'
for source in Jooble Adzuna Greenhouse Lever Ashby; do grep -q "\"source\":\"$source\"" "$body"; done

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "{\"query\":\"Backend-$run_key\",\"location\":\"Ciudad de México\"}" "$base_url/api/v1/job-refreshes")"
test "$status" = '202'
grep -q '"status":"COOLDOWN"' "$body"
grep -q '"scheduledConnectors":0' "$body"

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "{\"query\":\"FAIL_JOOBLE_$run_key\",\"location\":\"Ciudad de México\"}" "$base_url/api/v1/job-refreshes")"
test "$status" = '202'
failure_refresh_id="$(grep -o '"id":"[^"]*"' "$body" | head -1 | cut -d '"' -f 4)"

# La navegación continúa respondiendo mientras los conectores trabajan y uno falla.
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" "$base_url/api/v1/jobs/search?limit=5")"
test "$status" = '200'

attempt=0
while test "$attempt" -lt 60; do
  curl -fsS -H "Host: $host_header" -b "$cookies" "$base_url/api/v1/job-refreshes/$failure_refresh_id" > "$body"
  grep -q '"status":"PARTIAL"' "$body" && break
  attempt=$((attempt + 1))
  sleep 1
done
grep -q '"status":"PARTIAL"' "$body"
grep -q '"completedConnectors":4' "$body"
grep -q '"failedConnectors":1' "$body"

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X DELETE \
  -H "X-CSRF-TOKEN: $csrf" "$base_url/api/v1/me/account")"
test "$status" = '204'

printf 'phase4-e2e: OK (queue, cooldown, five adapters, deduplication, partial failure, navigation)\n'
