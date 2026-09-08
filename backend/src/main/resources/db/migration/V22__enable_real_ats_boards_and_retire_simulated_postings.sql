-- Lever and Ashby now use their public job-posting APIs. Enable only the
-- managed boards reviewed in V17; members never supply an arbitrary ATS URL.
UPDATE ingestion.public_job_board board
SET enabled = true, updated_at = now()
FROM jobs.source source
WHERE source.id = board.source_id
  AND source.source_key IN ('LEVER', 'ASHBY');

UPDATE ingestion.connector_query query
SET enabled = true, next_scheduled_at = now()
FROM ingestion.public_job_board board
WHERE query.board_id = board.id AND board.enabled;

-- The development adapters had emitted deliberately marked sample postings.
-- Close them rather than deleting audit data so the normal product view only
-- contains data from real sources after this migration.
UPDATE jobs.source_posting posting
SET status = 'CLOSED'
FROM jobs.source source
WHERE source.id = posting.source_id
  AND source.source_key IN ('GREENHOUSE', 'LEVER', 'ASHBY', 'JOOBLE', 'ADZUNA')
  AND posting.payload::text LIKE '%"simulated":true%';

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
