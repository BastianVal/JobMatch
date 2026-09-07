#!/bin/sh
set -eu

base_url="${BASE_URL:-http://caddy}"
mailpit_url="${MAILPIT_URL:-http://mailpit:8025}"
host_header="${APP_HOST:-localhost}"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
cookies="$work_dir/cookies.txt"
body="$work_dir/body.json"
email="phase5-$(date +%s)@example.com"
password='correct-horse-battery'
trajectory_id="$(cat /proc/sys/kernel/random/uuid)"

base64 -d /workspace/scripts/fixtures/cv-textual.pdf.b64 > "$work_dir/cv.pdf"
base64 -d /workspace/scripts/fixtures/cv-textual.docx.b64 > "$work_dir/cv.docx"

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
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" --data "{\"token\":\"$token\"}" \
  "$base_url/api/v1/auth/verify-email")"
test "$status" = '204'
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -c "$cookies" -X POST \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" \
  --data "{\"email\":\"$email\",\"password\":\"$password\"}" "$base_url/api/v1/auth/login")"
test "$status" = '204'

# Perfil base con trayectoria y Java: ambas propuestas del CV deberán detectarse como duplicados.
profile="$(printf '%s' '{
  "headline":"Backend engineer","summary":"Servicios Java","location":"Ciudad de México","seniority":"SENIOR",
  "targetRoles":[],"preferences":null,"excludedEmployers":[],
  "trajectory":[{"id":"TRAJECTORY_ID","type":"EMPLOYMENT","title":"Backend Developer","organization":"Example Corp","startYear":2022,"startMonth":1,"endYear":2024,"endMonth":6,"current":false}],
  "education":[],"certifications":[],"languages":[],
  "skills":[{"catalogSkillId":"12000000-0000-0000-0000-000000000001","proficiency":"ADVANCED","evidenceTrajectoryIds":["TRAJECTORY_ID"]}]
}' | sed "s/TRAJECTORY_ID/$trajectory_id/g")"
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X PUT \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" -H 'If-Match: "0"' \
  --data "$profile" "$base_url/api/v1/me/profile")"
test "$status" = '200'
grep -q '"version":1' "$body"

upload_and_wait() {
  source_file="$1"
  upload_name="$2"
  status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X POST \
    -H "X-CSRF-TOKEN: $csrf" -F "file=@$source_file;filename=$upload_name" "$base_url/api/v1/me/cv-imports")"
  test "$status" = '202'
  import_id="$(grep -o '"id":"[^"]*"' "$body" | head -1 | cut -d '"' -f 4)"
  document_id="$(grep -o '"documentId":"[^"]*"' "$body" | head -1 | cut -d '"' -f 4)"
  attempt=0
  while test "$attempt" -lt 60; do
    curl -fsS -H "Host: $host_header" -b "$cookies" "$base_url/api/v1/me/cv-imports/$import_id" > "$body"
    grep -q '"status":"READY"' "$body" && break
    grep -q '"status":"FAILED"' "$body" && { cat "$body"; return 1; }
    attempt=$((attempt + 1))
    sleep 1
  done
  grep -q '"status":"READY"' "$body"
  grep -q '"extractorVersion":"tika-3.3.2-rules-2"' "$body"
  test "$(grep -o '"decision":"PENDING"' "$body" | wc -l | tr -d ' ')" -ge '5'
}

decide_all() {
  import_id="$1"
  mode="$2"
  candidates="$(grep -o '"id":"[^"]*","type":"[^"]*","payloadVersion"' "$body")"
  printf '%s\n' "$candidates" | while IFS= read -r candidate; do
    candidate_id="$(printf '%s' "$candidate" | cut -d '"' -f 4)"
    if test "$mode" = 'skip'; then
      request='{"decision":"SKIPPED"}'
    else
      request='{"decision":"ACCEPTED"}'
    fi
    status="$(curl -sS -H "Host: $host_header" -o "$work_dir/decision.json" -w '%{http_code}' -b "$cookies" -X PUT \
      -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" -H 'If-Match: "1"' \
      --data "$request" "$base_url/api/v1/me/cv-imports/$import_id/candidates/$candidate_id")"
    if test "$status" = '409' && test "$mode" = 'accept'; then
      status="$(curl -sS -H "Host: $host_header" -o "$work_dir/decision.json" -w '%{http_code}' -b "$cookies" -X PUT \
        -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $csrf" -H 'If-Match: "1"' \
        --data '{"decision":"ACCEPTED","duplicateResolution":"REPLACE_EXISTING"}' \
        "$base_url/api/v1/me/cv-imports/$import_id/candidates/$candidate_id")"
    fi
    test "$status" = '200'
  done
}

upload_and_wait "$work_dir/cv.pdf" 'cv-textual.pdf'
pdf_import_id="$import_id"
pdf_document_id="$document_id"

# Confirmar antes de revisar debe fallar: ninguna propuesta se acepta implícitamente.
status="$(curl -sS -H "Host: $host_header" -o "$work_dir/early.json" -w '%{http_code}' -b "$cookies" -X POST \
  -H "X-CSRF-TOKEN: $csrf" "$base_url/api/v1/me/cv-imports/$pdf_import_id/confirm")"
test "$status" = '409'
grep -q 'CV_IMPORT_NOT_READY' "$work_dir/early.json"

decide_all "$pdf_import_id" accept
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X POST \
  -H "X-CSRF-TOKEN: $csrf" "$base_url/api/v1/me/cv-imports/$pdf_import_id/confirm")"
test "$status" = '200'
grep -q '"version":2' "$body"
for value in 'Computer Engineering' 'Spring Professional' 'Spring Boot' 'PostgreSQL' '"professionalMonths":30'; do grep -q "$value" "$body"; done

# Confirmación idempotente.
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X POST \
  -H "X-CSRF-TOKEN: $csrf" "$base_url/api/v1/me/cv-imports/$pdf_import_id/confirm")"
test "$status" = '200'
grep -q '"version":2' "$body"

# El mismo contenido activo no puede almacenarse dos veces.
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X POST \
  -H "X-CSRF-TOKEN: $csrf" -F "file=@$work_dir/cv.pdf;filename=copy.pdf" "$base_url/api/v1/me/cv-imports")"
test "$status" = '409'
grep -q 'DUPLICATE_CV' "$body"

upload_and_wait "$work_dir/cv.docx" 'cv-textual.docx'
docx_import_id="$import_id"
docx_document_id="$document_id"
decide_all "$docx_import_id" skip
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X POST \
  -H "X-CSRF-TOKEN: $csrf" "$base_url/api/v1/me/cv-imports/$docx_import_id/confirm")"
test "$status" = '200'
grep -q '"version":2' "$body"

# La extensión no sustituye la validación del contenido.
printf 'esto no es un PDF' > "$work_dir/fake.pdf"
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X POST \
  -H "X-CSRF-TOKEN: $csrf" -F "file=@$work_dir/fake.pdf;filename=fake.pdf" "$base_url/api/v1/me/cv-imports")"
test "$status" = '400'
grep -q 'INVALID_CV_FILE' "$body"

# El propietario puede descargar el archivo original antes de eliminarlo.
status="$(curl -sS -H "Host: $host_header" -o "$work_dir/downloaded.pdf" -w '%{http_code}' -b "$cookies" \
  "$base_url/api/v1/me/cv-documents/$pdf_document_id/download")"
test "$status" = '200'
cmp "$work_dir/cv.pdf" "$work_dir/downloaded.pdf"

for id in "$pdf_document_id" "$docx_document_id"; do
  status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X DELETE \
    -H "X-CSRF-TOKEN: $csrf" "$base_url/api/v1/me/cv-documents/$id")"
  test "$status" = '204'
done
status="$(curl -sS -H "Host: $host_header" -o "$body" -w '%{http_code}' -b "$cookies" -X DELETE \
  -H "X-CSRF-TOKEN: $csrf" "$base_url/api/v1/me/account")"
test "$status" = '204'

printf 'phase5-e2e: OK (PDF, DOCX, worker, proposals, duplicates, confirmation, download, cleanup)\n'
