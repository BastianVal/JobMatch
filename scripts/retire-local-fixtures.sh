#!/usr/bin/env sh
set -eu

if [ "${1:-}" != "--confirm" ]; then
  echo "This closes only LOCAL_FIXTURES postings in the current database. Run: sh scripts/retire-local-fixtures.sh --confirm"
  exit 2
fi

docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U jobmatch -d jobmatch <<'SQL'
BEGIN;
UPDATE jobs.source_posting posting
SET status = 'CLOSED'
FROM jobs.source source
WHERE source.id = posting.source_id
  AND source.source_key = 'LOCAL_FIXTURES'
  AND posting.status = 'ACTIVE';

UPDATE jobs.job_source_link link
SET active = false
FROM jobs.source_posting posting
JOIN jobs.source source ON source.id = posting.source_id
WHERE link.source_posting_id = posting.id
  AND source.source_key = 'LOCAL_FIXTURES';

UPDATE jobs.canonical_job job
SET status = 'CLOSED', updated_at = now(), version = version + 1
WHERE job.status = 'ACTIVE' AND NOT EXISTS (
    SELECT 1 FROM jobs.job_source_link link
    WHERE link.canonical_job_id = job.id AND link.active
);
COMMIT;
SQL

echo "Closed LOCAL_FIXTURES postings. They remain in the audit history and can be regenerated for load tests."
