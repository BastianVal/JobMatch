#!/bin/sh
set -eu

base_url="${BASE_URL:-http://caddy}"
mailpit_url="${MAILPIT_URL:-http://mailpit:8025}"
host_header="${APP_HOST:-localhost}"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
cookies="$work_dir/cookies.txt"
body="$work_dir/body.json"
email="phase1-$(date +%s)@example.com"
old_password='correct-horse-battery'
new_password='correct-horse-battery-updated'

csrf_json="$(curl -fsS -H "Host: $host_header" -c "$cookies" "$base_url/api/v1/auth/csrf")"
csrf="$(printf '%s' "$csrf_json" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
test -n "$csrf"

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X POST \
  -H 'Content-Type: application/json' \
  --data "{\"email\":\"$email\",\"password\":\"$old_password\"}" \
  "$base_url/api/v1/auth/register")"
test "$status" = '403'

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -c "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "{\"email\":\"$email\",\"password\":\"$old_password\"}" \
  "$base_url/api/v1/auth/register")"
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
verification_token="$(printf '%s' "$message" | grep -o 'token=[A-Za-z0-9_.-]*' | head -1 | cut -d= -f2)"
test -n "$verification_token"

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -c "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "{\"token\":\"$verification_token\"}" "$base_url/api/v1/auth/verify-email")"
test "$status" = '204'

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "{\"token\":\"$verification_token\"}" "$base_url/api/v1/auth/verify-email")"
test "$status" = '400'

session_before="$(awk '$6 == "jobmatch_session" { print $7 }' "$cookies")"
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -c "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "{\"email\":\"$email\",\"password\":\"$old_password\"}" \
  "$base_url/api/v1/auth/login")"
test "$status" = '204'
session_after="$(awk '$6 == "jobmatch_session" { print $7 }' "$cookies")"
test "$session_before" != "$session_after"

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" "$base_url/api/v1/me/account")"
test "$status" = '200'
grep -q "$email" "$body"

status_unknown="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data '{"email":"unknown@example.com"}' "$base_url/api/v1/auth/password/forgot")"
status_known="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "{\"email\":\"$email\"}" "$base_url/api/v1/auth/password/forgot")"
test "$status_unknown" = '202'
test "$status_known" = '202'

sleep 2
messages="$(curl -fsS "$mailpit_url/api/v1/messages?limit=1")"
message_id="$(printf '%s' "$messages" | grep -o '"ID":"[^"]*"' | head -1 | cut -d '"' -f 4)"
message="$(curl -fsS "$mailpit_url/api/v1/message/$message_id")"
reset_token="$(printf '%s' "$message" | grep -o 'token=[A-Za-z0-9_.-]*' | head -1 | cut -d= -f2)"
test -n "$reset_token"

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "{\"token\":\"$reset_token\",\"newPassword\":\"$new_password\"}" \
  "$base_url/api/v1/auth/password/reset")"
test "$status" = '204'
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" "$base_url/api/v1/me/account")"
test "$status" = '401'

csrf_json="$(curl -fsS -H "Host: $host_header" -c "$cookies" "$base_url/api/v1/auth/csrf")"
csrf="$(printf '%s' "$csrf_json" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -c "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "{\"email\":\"$email\",\"password\":\"$new_password\"}" \
  "$base_url/api/v1/auth/login")"
test "$status" = '204'
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X DELETE \
  -H "X-CSRF-TOKEN: $csrf" "$base_url/api/v1/me/account")"
test "$status" = '204'
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" "$base_url/api/v1/me/account")"
test "$status" = '401'

# La cuenta eliminada no puede iniciar una sesión nueva, aun con las credenciales originales.
csrf_json="$(curl -fsS -H "Host: $host_header" -c "$cookies" "$base_url/api/v1/auth/csrf")"
csrf="$(printf '%s' "$csrf_json" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -c "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "{\"email\":\"$email\",\"password\":\"$new_password\"}" \
  "$base_url/api/v1/auth/login")"
test "$status" = '401'

printf 'phase1-e2e: OK (csrf, verify, fixation, session, recovery, revocation, ownership)\n'
