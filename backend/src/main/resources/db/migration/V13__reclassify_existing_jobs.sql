-- Existing vacancies may still reference one of the broad, non-selectable v1
-- families. Resolve them against the published v2 aliases using the same
-- deterministic "longest matching alias" rule as ingestion.
WITH ranked_match AS (
    SELECT job.id AS job_id,
           role.id AS role_id,
           row_number() OVER (
               PARTITION BY job.id
               ORDER BY
                   CASE WHEN unaccent(lower(job.title)) = unaccent(lower(alias.alias::text)) THEN 0 ELSE 1 END,
                   length(alias.alias::text) DESC,
                   role.display_name,
                   role.public_id
           ) AS position
    FROM jobs.canonical_job job
    JOIN catalog.role_family existing_family ON existing_family.id = job.role_family_id
    JOIN catalog.role_alias alias
      ON unaccent(lower(job.title)) LIKE '%' || unaccent(lower(alias.alias::text)) || '%'
    JOIN catalog.role_family role ON role.id = alias.role_family_id
    JOIN catalog.catalog_version version ON version.id = role.catalog_version_id
    WHERE existing_family.selectable = false
      AND role.active = true
      AND role.selectable = true
      AND version.status = 'PUBLISHED'
), resolved AS (
    SELECT job_id, role_id FROM ranked_match WHERE position = 1
)
UPDATE jobs.canonical_job job
SET role_family_id = resolved.role_id,
    version = job.version + 1,
    updated_at = now()
FROM resolved
WHERE job.id = resolved.job_id;
