#!/bin/sh
set -eu
base="${BASE_URL:-http://caddy}";mailpit="${MAILPIT_URL:-http://mailpit:8025}";host="${APP_HOST:-localhost}"
tmp="$(mktemp -d)";trap 'rm -rf "$tmp"' EXIT
cookies="$tmp/cookies";body="$tmp/body";email="phase7-$(date +%s)@example.com";password='correct-horse-battery'

csrf_json="$(curl -fsS -H "Host: $host" -c "$cookies" "$base/api/v1/auth/csrf")"
csrf="$(printf '%s' "$csrf_json" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
status="$(curl -sS -o "$body" -w '%{http_code}' -H "Host: $host" -b "$cookies" -c "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" --data "{\"email\":\"$email\",\"password\":\"$password\"}" "$base/api/v1/auth/register")"
test "$status" = 202
message_id='';attempt=0
while test "$attempt" -lt 15;do
  messages="$(curl -fsS "$mailpit/api/v1/messages?limit=1")"
  message_id="$(printf '%s' "$messages"|grep -o '"ID":"[^"]*"'|head -1|cut -d '"' -f 4)"
  if test -n "$message_id";then message="$(curl -fsS "$mailpit/api/v1/message/$message_id")";printf '%s' "$message"|grep -q "$email"&&break;fi
  message_id='';attempt=$((attempt+1));sleep 1
done
test -n "$message_id"
token="$(printf '%s' "$message"|grep -o 'token=[A-Za-z0-9_.-]*'|head -1|cut -d= -f2)"
status="$(curl -sS -o "$body" -w '%{http_code}' -H "Host: $host" -b "$cookies" -c "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" --data "{\"token\":\"$token\"}" "$base/api/v1/auth/verify-email")";test "$status" = 204
status="$(curl -sS -o "$body" -w '%{http_code}' -H "Host: $host" -b "$cookies" -c "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" --data "{\"email\":\"$email\",\"password\":\"$password\"}" "$base/api/v1/auth/login")";test "$status" = 204

curl -fsS -H "Host: $host" -b "$cookies" "$base/api/v1/jobs/search?limit=1" > "$body"
job="$(grep -o '"id":"[^"]*"' "$body"|head -1|cut -d '"' -f 4)";test -n "$job"

# NEW se deriva de impresiones; repetir un UUID en el lote no duplica filas.
curl -fsS -H "Host: $host" -b "$cookies" "$base/api/v1/me/job-activity?jobId=$job" > "$body";grep -q '"new":true' "$body"
status="$(curl -sS -o "$body" -w '%{http_code}' -H "Host: $host" -b "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" --data "{\"jobIds\":[\"$job\",\"$job\"]}" "$base/api/v1/me/job-impressions")"
test "$status" = 200;grep -q '"recorded":1' "$body"
curl -fsS -H "Host: $host" -b "$cookies" "$base/api/v1/me/job-activity?jobId=$job" > "$body"
grep -q '"new":false' "$body";grep -q '"firstViewedAt"' "$body";grep -q '"lastViewedAt"' "$body"

put_tracking(){
  version="$1";target="$2";key="$3";note="${4:-}"
  curl -sS -o "$body" -w '%{http_code}' -H "Host: $host" -b "$cookies" -X PUT \
    -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" -H "If-Match: \"$version\"" -H "Idempotency-Key: $key" \
    --data "{\"state\":\"$target\",\"note\":\"$note\"}" "$base/api/v1/me/jobs/$job/tracking"
}

# Alta y retry con la misma clave devuelven el mismo recurso sin otra transición.
status="$(put_tracking 0 SAVED first 'Revisar requisitos')";test "$status" = 200;grep -q '"version":1' "$body"
tracking="$(grep -o '"id":"[^"]*"' "$body"|head -1|cut -d '"' -f 4)"
status="$(put_tracking 0 SAVED first 'Revisar requisitos')";test "$status" = 200;grep -q "\"id\":\"$tracking\"" "$body";grep -q '"version":1' "$body"
status="$(put_tracking 0 APPLIED first)";test "$status" = 409;grep -q 'IDEMPOTENCY_KEY_REUSED' "$body"

# Una versión obsoleta y un salto de etapa son conflictos explícitos.
status="$(put_tracking 0 APPLIED stale)";test "$status" = 409;grep -q 'VERSION_CONFLICT' "$body"
status="$(put_tracking 1 OFFER skip-stage)";test "$status" = 409;grep -q 'INVALID_TRACKING_TRANSITION' "$body"

status="$(put_tracking 1 APPLIED applied)";test "$status" = 200
status="$(put_tracking 2 INTERVIEW interview)";test "$status" = 200
status="$(put_tracking 3 OFFER offer)";test "$status" = 200
status="$(put_tracking 4 ACCEPTED accepted)";test "$status" = 200;grep -q '"version":5' "$body"

curl -fsS -H "Host: $host" -b "$cookies" "$base/api/v1/me/tracking?state=ACCEPTED&limit=10" > "$body"
grep -q "\"id\":\"$tracking\"" "$body";grep -q '"resultingVersion":5' "$body"
curl -fsS -H "Host: $host" -b "$cookies" "$base/api/v1/me/tracking?limit=10" > "$body"
grep -q "\"id\":\"$tracking\"" "$body"
status="$(put_tracking 5 SAVED terminal)";test "$status" = 409;grep -q 'INVALID_TRACKING_TRANSITION' "$body"

status="$(curl -sS -o "$body" -w '%{http_code}' -H "Host: $host" -b "$cookies" -X DELETE -H "X-CSRF-TOKEN: $csrf" "$base/api/v1/me/account")"
test "$status" = 204
printf 'phase7-e2e: OK (impressions, NEW, idempotency, optimistic locking, lifecycle, history)\n'
