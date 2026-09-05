#!/bin/sh
set -eu

base_url="${BASE_URL:-http://caddy}"
mailpit_url="${MAILPIT_URL:-http://mailpit:8025}"
host_header="${APP_HOST:-localhost}"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
cookies="$work_dir/cookies.txt"
body="$work_dir/body.json"
email="phase2-$(date +%s)@example.com"
password='correct-horse-battery'
employment_id="$(cat /proc/sys/kernel/random/uuid)"
internship_id="$(cat /proc/sys/kernel/random/uuid)"

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

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" "$base_url/api/v1/catalog/roles?q=desarrollo")"
test "$status" = '200'
grep -q 'Desarrollo de software' "$body"
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" "$base_url/api/v1/catalog/skills?q=java")"
test "$status" = '200'
grep -q '"catalogVersion":1' "$body"

payload="$(printf '%s' '{
  "headline":"Backend engineer",
  "summary":"Construyo servicios confiables.",
  "location":"Ciudad de México",
  "seniority":"SENIOR",
  "targetRoles":[{"roleFamilyId":"11000000-0000-0000-0000-000000000001","priority":1}],
  "preferences":{"remoteMode":"HYBRID","employmentType":"FULL_TIME","minimumMonthlySalary":50000,"currency":"MXN","willingToRelocate":false},
  "excludedEmployers":["Ácme","acme"],
  "trajectory":[
    {"id":"EMPLOYMENT_ID","type":"EMPLOYMENT","title":"Backend developer","organization":"Example","startYear":2024,"startMonth":1,"endYear":2024,"endMonth":3,"current":false},
    {"id":"INTERNSHIP_ID","type":"INTERNSHIP","title":"Engineering intern","organization":"Example","startYear":2024,"startMonth":2,"endYear":2024,"endMonth":4,"current":false}
  ],
  "education":[{"institution":"UNAM","degree":"Ingeniería","fieldOfStudy":"Computación","startYear":2018,"endYear":2022}],
  "certifications":[{"name":"Spring Professional","issuer":"Example","issuedYear":2025}],
  "languages":[{"code":"es","name":"Español","proficiency":"NATIVE"},{"code":"en","name":"Inglés","proficiency":"PROFESSIONAL"}],
  "skills":[
    {"catalogSkillId":"12000000-0000-0000-0000-000000000001","proficiency":"ADVANCED","evidenceTrajectoryIds":["EMPLOYMENT_ID","INTERNSHIP_ID"]},
    {"customName":"Tecnología interna","proficiency":"INTERMEDIATE","evidenceTrajectoryIds":[]}
  ]
}' | sed "s/EMPLOYMENT_ID/$employment_id/g; s/INTERNSHIP_ID/$internship_id/g")"

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X PUT \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" -H 'If-Match: "0"' \
  --data "$payload" "$base_url/api/v1/me/profile")"
test "$status" = '200'
grep -q '"version":1' "$body"
grep -q '"professionalMonths":3' "$body"
grep -q '"weightedPracticalMonths":3.70' "$body"
grep -q '"matchEligible":false' "$body"
test "$(grep -o 'employerName' "$body" | wc -l)" = '0'
test "$(grep -o 'Ácme' "$body" | wc -l)" = '1'

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X PUT \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" -H 'If-Match: "0"' \
  --data "$payload" "$base_url/api/v1/me/profile")"
test "$status" = '409'
grep -q 'VERSION_CONFLICT' "$body"

for endpoint in target-roles trajectory skills; do
  status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" "$base_url/api/v1/me/$endpoint")"
  test "$status" = '200'
done

status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X DELETE \
  -H "X-CSRF-TOKEN: $csrf" "$base_url/api/v1/me/account")"
test "$status" = '204'

printf 'phase2-e2e: OK (catalog, aggregate profile, evidence, projections, versioning, cleanup)\n'
