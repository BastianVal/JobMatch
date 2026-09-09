-- Generic queries created before public-board synchronization can still invoke
-- the local Jooble/Adzuna simulators. Disable those legacy queries until real
-- credentialed aggregators are introduced.
UPDATE ingestion.connector_query query
SET enabled = false
FROM jobs.source source
WHERE source.id = query.source_id
  AND query.board_id IS NULL
  AND source.source_key IN ('JOOBLE', 'ADZUNA', 'GREENHOUSE', 'LEVER', 'ASHBY');

UPDATE jobs.source_posting posting
SET status = 'CLOSED'
WHERE posting.status = 'ACTIVE'
  AND EXISTS (
      SELECT 1
      FROM ingestion.source_payload payload
      JOIN jobs.source source ON source.id = posting.source_id
      WHERE payload.source_posting_id = posting.id
        AND payload.payload @> jsonb_build_object('simulated', true)
        AND source.source_key IN ('JOOBLE', 'ADZUNA', 'GREENHOUSE', 'LEVER', 'ASHBY')
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
