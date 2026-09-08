-- V22 enabled the real ATS connectors. Simulated data is retained in
-- ingestion.source_payload (not jobs.source_posting), so close precisely the
-- postings that have an audited simulated payload.
UPDATE jobs.source_posting posting
SET status = 'CLOSED'
WHERE posting.status = 'ACTIVE'
  AND EXISTS (
      SELECT 1
      FROM ingestion.source_payload payload
      JOIN jobs.source source ON source.id = posting.source_id
      WHERE payload.source_posting_id = posting.id
        AND source.source_key IN ('GREENHOUSE', 'LEVER', 'ASHBY', 'JOOBLE', 'ADZUNA')
        AND payload.payload @> jsonb_build_object('simulated', true)
  );

UPDATE jobs.job_source_link link
SET active = false
FROM jobs.source_posting posting
WHERE link.source_posting_id = posting.id AND posting.status = 'CLOSED';

UPDATE jobs.canonical_job job
SET status = 'CLOSED', updated_at = now(), version = version + 1
WHERE job.status = 'ACTIVE' AND NOT EXISTS (
    SELECT 1 FROM jobs.job_source_link link
    WHERE link.canonical_job_id = job.id AND link.active
);
