UPDATE jobs.job_source_link link
SET link_type = 'OFFICIAL'
FROM jobs.source_posting posting
JOIN jobs.source source ON source.id = posting.source_id
WHERE link.source_posting_id = posting.id
  AND source.source_key IN ('GREENHOUSE', 'LEVER', 'ASHBY');
