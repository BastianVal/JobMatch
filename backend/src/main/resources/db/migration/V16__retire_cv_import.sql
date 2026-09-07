-- CV import was removed from the current product scope. Preserve the V8 schema for
-- Flyway checksum compatibility and historical records, but stop orphaned work.
UPDATE ops.background_task
SET status = 'FAILED',
    leased_until = NULL,
    leased_by = NULL,
    last_error_code = 'FEATURE_RETIRED',
    updated_at = now()
WHERE task_type = 'EXTRACT_CV'
  AND status IN ('PENDING', 'RUNNING');

UPDATE cvimport.import_run
SET status = 'FAILED',
    safe_error_code = 'FEATURE_RETIRED',
    finished_at = COALESCE(finished_at, now())
WHERE status IN ('QUEUED', 'PROCESSING');
