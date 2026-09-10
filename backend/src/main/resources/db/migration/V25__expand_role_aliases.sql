-- Public boards commonly use Engineer instead of Developer. Keep those variants
-- in the normalized catalog so ingestion and role filters do not depend on an
-- exact Spanish or English title.
INSERT INTO catalog.role_alias(role_family_id, alias)
SELECT role.id, seed.alias
FROM catalog.role_family role
JOIN (VALUES
    ('java-developer', 'Java Engineer'), ('java-developer', 'Java Software Engineer'), ('java-developer', 'Backend Java Engineer'),
    ('python-developer', 'Python Engineer'), ('python-developer', 'Python Software Engineer'),
    ('dotnet-developer', '.NET Engineer'), ('dotnet-developer', 'Dotnet Engineer'),
    ('nodejs-developer', 'NodeJS Engineer'), ('nodejs-developer', 'Node.js Engineer'), ('nodejs-developer', 'NodeJS Backend Engineer'),
    ('frontend-developer', 'Frontend Engineer'), ('frontend-developer', 'Software Engineer Frontend'),
    ('backend-developer', 'Backend Engineer'), ('backend-developer', 'Back End Engineer'), ('backend-developer', 'Backend Software Engineer'),
    ('fullstack-developer', 'Full Stack Engineer'), ('fullstack-developer', 'Fullstack Engineer'), ('fullstack-developer', 'Full Stack and Backend NodeJS TypeScript JavaScript Engineer'),
    ('react-developer', 'React Engineer'), ('angular-developer', 'Angular Engineer'), ('vuejs-developer', 'Vue.js Engineer'),
    ('data-engineer', 'Data Engineering Specialist'), ('devops-engineer', 'DevOps Specialist'),
    ('qa-engineer', 'Quality Assurance Engineer'), ('qa-automation-engineer', 'Test Automation Engineer')
) seed(slug, alias) ON seed.slug=role.slug
JOIN catalog.catalog_version version ON version.id=role.catalog_version_id AND version.status='PUBLISHED'
ON CONFLICT DO NOTHING;

-- Bring previously ingested but unclassified titles into the expanded taxonomy.
WITH candidates AS (
    SELECT job.id AS job_id, role.id AS role_id,
           row_number() OVER (PARTITION BY job.id ORDER BY length(alias.alias::text) DESC, role.public_id) AS position
    FROM jobs.canonical_job job
    JOIN catalog.role_alias alias ON unaccent(lower(job.title)) LIKE '%' || unaccent(lower(alias.alias::text)) || '%'
    JOIN catalog.role_family role ON role.id=alias.role_family_id
    JOIN catalog.catalog_version version ON version.id=role.catalog_version_id AND version.status='PUBLISHED'
    WHERE job.role_family_id IS NULL AND role.active AND role.selectable
), resolved AS (
    SELECT job_id, role_id FROM candidates WHERE position=1
)
UPDATE jobs.canonical_job job
SET role_family_id=resolved.role_id, version=job.version + 1, updated_at=now()
FROM resolved WHERE job.id=resolved.job_id;
