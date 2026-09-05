#!/bin/sh
set -eu

base_url="${BASE_URL:-http://caddy}"
mailpit_url="${MAILPIT_URL:-http://mailpit:8025}"
host_header="${APP_HOST:-localhost}"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
cookies="$work_dir/cookies.txt"
body="$work_dir/body.json"
email="phase6-$(date +%s)@example.com"
run_key="$(date +%s)"
password='correct-horse-battery'
employment_id="$(cat /proc/sys/kernel/random/uuid)"
project_id="$(cat /proc/sys/kernel/random/uuid)"

csrf_json="$(curl -fsS -H "Host: $host_header" -c "$cookies" "$base_url/api/v1/auth/csrf")"
csrf="$(printf '%s' "$csrf_json" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -c "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "{\"email\":\"$email\",\"password\":\"$password\"}" "$base_url/api/v1/auth/register")"
test "$status" = '202'
message_id='';attempt=0
while test "$attempt" -lt 15; do
  messages="$(curl -fsS "$mailpit_url/api/v1/messages?limit=1")"
  message_id="$(printf '%s' "$messages" | grep -o '"ID":"[^"]*"' | head -1 | cut -d '"' -f 4)"
  if test -n "$message_id"; then message="$(curl -fsS "$mailpit_url/api/v1/message/$message_id")";printf '%s' "$message" | grep -q "$email" && break;fi
  message_id='';attempt=$((attempt+1));sleep 1
done
test -n "$message_id"
token="$(printf '%s' "$message" | grep -o 'token=[A-Za-z0-9_.-]*' | head -1 | cut -d= -f2)"
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -c "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" --data "{\"token\":\"$token\"}" "$base_url/api/v1/auth/verify-email")"
test "$status" = '204'
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -c "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" --data "{\"email\":\"$email\",\"password\":\"$password\"}" "$base_url/api/v1/auth/login")"
test "$status" = '204'

profile_payload() {
  excluded="$1"
  printf '%s' '{
    "headline":"Backend engineer","summary":"Java APIs and PostgreSQL","location":"Ciudad de México","seniority":"SENIOR",
    "targetRoles":[{"roleFamilyId":"11000000-0000-0000-0000-000000000001","priority":1}],
    "preferences":{"remoteMode":"ANY","employmentType":"ANY","currency":"MXN","willingToRelocate":false},
    "excludedEmployers":EXCLUDED,
    "trajectory":[
      {"id":"EMPLOYMENT_ID","type":"EMPLOYMENT","title":"Backend Developer","organization":"Example","startYear":2021,"startMonth":1,"endYear":2024,"endMonth":12,"current":false},
      {"id":"PROJECT_ID","type":"OPEN_SOURCE","title":"Java API","organization":"Community","startYear":2023,"startMonth":1,"endYear":2023,"endMonth":12,"current":false}
    ],
    "education":[],"certifications":[],"languages":[],
    "skills":[
      {"catalogSkillId":"12000000-0000-0000-0000-000000000001","proficiency":"ADVANCED","evidenceTrajectoryIds":["EMPLOYMENT_ID","PROJECT_ID"]},
      {"catalogSkillId":"12000000-0000-0000-0000-000000000003","proficiency":"ADVANCED","evidenceTrajectoryIds":["EMPLOYMENT_ID"]}
    ]
  }' | sed "s/EMPLOYMENT_ID/$employment_id/g;s/PROJECT_ID/$project_id/g;s/EXCLUDED/$excluded/g"
}

payload="$(profile_payload '[]')"
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X PUT \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" -H 'If-Match: "0"' --data "$payload" "$base_url/api/v1/me/profile")"
test "$status" = '200'

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "{\"roleFamilyId\":\"11000000-0000-0000-0000-000000000001\",\"query\":\"Phase6 Backend $run_key\",\"location\":\"Ciudad de México\"}" \
  "$base_url/api/v1/job-refreshes")"
test "$status" = '202'
refresh_id="$(grep -o '"id":"[^"]*"' "$body" | head -1 | cut -d '"' -f 4)"
attempt=0
while test "$attempt" -lt 60; do
  curl -fsS -H "Host: $host_header" -b "$cookies" "$base_url/api/v1/job-refreshes/$refresh_id" > "$body"
  grep -q '"status":"COMPLETED"' "$body" && break
  attempt=$((attempt+1));sleep 1
done
grep -q '"status":"COMPLETED"' "$body"

curl -fsS -H "Host: $host_header" -b "$cookies" "$base_url/api/v1/jobs/search?q=Tecnologia%20Ejemplo&limit=10" > "$body"
job_id="$(grep -o '"id":"[^"]*"' "$body" | head -1 | cut -d '"' -f 4)"
test -n "$job_id"

# Primera evaluación crea snapshots y la segunda usa exactamente la misma clave de versiones.
curl -fsS -H "Host: $host_header" -b "$cookies" "$base_url/api/v1/jobs/$job_id/match" > "$body"
grep -q '"scoringVersion":"score-1"' "$body"
grep -q '"profileVersion":1' "$body"
grep -q '"requirement"' "$body"
grep -q '"evidence"' "$body"
first_match_id="$(grep -o '"id":"[^"]*"' "$body" | head -1 | cut -d '"' -f 4)"
curl -fsS -H "Host: $host_header" -b "$cookies" "$base_url/api/v1/jobs/$job_id/match" > "$body"
grep -q '"cached":true' "$body"
grep -q "\"id\":\"$first_match_id\"" "$body"

curl -fsS -H "Host: $host_header" -b "$cookies" "$base_url/api/v1/me/recommendations?limit=10" > "$body"
grep -q "$job_id" "$body"
grep -q 'ROLE_RESPONSIBILITIES' "$body"
grep -q 'TECHNOLOGIES_KNOWLEDGE' "$body"

# Una nueva versión de perfil produce otra clave, sin sobrescribir el resultado anterior.
payload="$(profile_payload '[]')"
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X PUT \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" -H 'If-Match: "1"' --data "$payload" "$base_url/api/v1/me/profile")"
test "$status" = '200'
curl -fsS -H "Host: $host_header" -b "$cookies" "$base_url/api/v1/jobs/$job_id/match" > "$body"
grep -q '"profileVersion":2' "$body"
second_match_id="$(grep -o '"id":"[^"]*"' "$body" | head -1 | cut -d '"' -f 4)"
test "$first_match_id" != "$second_match_id"

# El resultado histórico sigue reconstruible desde sus snapshots, aunque el perfil actual ya sea versión 2.
curl -fsS -H "Host: $host_header" -b "$cookies" "$base_url/api/v1/matches/$first_match_id" > "$body"
grep -q "\"id\":\"$first_match_id\"" "$body"
grep -q '"profileVersion":1' "$body"
grep -q '"requirement"' "$body"
grep -q '"evidence"' "$body"

# Las exclusiones siempre se aplican a recomendaciones.
payload="$(profile_payload '["Tecnología Ejemplo"]')"
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X PUT \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" -H 'If-Match: "2"' --data "$payload" "$base_url/api/v1/me/profile")"
test "$status" = '200'
curl -fsS -H "Host: $host_header" -b "$cookies" "$base_url/api/v1/me/recommendations?limit=10" > "$body"
if grep -q "$job_id" "$body";then echo 'excluded employer leaked into recommendations';exit 1;fi

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X DELETE \
  -H "X-CSRF-TOKEN: $csrf" "$base_url/api/v1/me/account")"
test "$status" = '204'
printf 'phase6-e2e: OK (facts, score, evidence, cache versions, target-role recommendations, exclusions)\n'
