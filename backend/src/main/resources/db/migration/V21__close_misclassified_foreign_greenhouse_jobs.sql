-- Older Greenhouse imports used the managed-board country as a fallback even when
-- the provider wrote a foreign country followed by a qualifier, e.g. Argentina (Remote).
WITH foreign_greenhouse_postings AS (
    SELECT DISTINCT payload.source_posting_id
    FROM ingestion.source_payload payload
    JOIN jobs.source_posting posting ON posting.id = payload.source_posting_id
    JOIN jobs.source source ON source.id = posting.source_id
    WHERE source.source_key = 'GREENHOUSE'
      AND COALESCE(payload.payload #>> '{location,name}', '') ~* '\m(argentina|united states|usa|canada|brazil|brasil|colombia|chile|spain|españa|united kingdom|uk|india|germany|france|australia)\M'
)
UPDATE jobs.source_posting posting
SET status = 'CLOSED'
FROM foreign_greenhouse_postings foreign_posting
WHERE posting.id = foreign_posting.source_posting_id;

UPDATE jobs.job_source_link link
SET active = false
FROM jobs.source_posting posting
WHERE link.source_posting_id = posting.id
  AND posting.status = 'CLOSED';

UPDATE jobs.canonical_job job
SET status = 'CLOSED', updated_at = now(), version = version + 1
WHERE job.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM jobs.job_source_link link
      WHERE link.canonical_job_id = job.id AND link.active
  );
