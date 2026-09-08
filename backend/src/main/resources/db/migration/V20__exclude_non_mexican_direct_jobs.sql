UPDATE jobs.source_posting posting
SET status = 'CLOSED'
FROM jobs.job_source_link link,
     jobs.canonical_job job,
     jobs.job_location location,
     jobs.source source
WHERE link.source_posting_id = posting.id
  AND job.id = link.canonical_job_id
  AND location.canonical_job_id = job.id
  AND source.id = posting.source_id
  AND source.source_key = 'GREENHOUSE'
  AND location.country_code <> 'MX';

UPDATE jobs.job_source_link link SET active = false
FROM jobs.source_posting posting
WHERE link.source_posting_id = posting.id AND posting.status = 'CLOSED';

UPDATE jobs.canonical_job job SET status = 'CLOSED', updated_at = now(), version = version + 1
WHERE job.status = 'ACTIVE' AND NOT EXISTS (
    SELECT 1 FROM jobs.job_source_link link WHERE link.canonical_job_id = job.id AND link.active
);
