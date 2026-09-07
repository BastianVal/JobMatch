\set ON_ERROR_STOP on

BEGIN;

CREATE TEMP TABLE fixture_job ON COMMIT DROP AS
WITH selectable_roles AS (
    SELECT public_id AS role_public_id, slug AS role_slug, display_name AS role_name,
           row_number() OVER (ORDER BY slug) AS role_no
    FROM catalog.role_family
    WHERE active AND selectable
), role_count AS (
    SELECT count(*) AS value FROM selectable_roles
)
SELECT series AS fixture_no,
       ((series - 1) % 200) + 1 AS employer_no,
       role.role_no,
       role.role_public_id,
       role.role_slug,
       md5('job:' || series)::uuid AS job_public_id,
       'local-fixture-' || series AS identity_key,
       role.role_name || ' ' || series AS title,
       CASE ((series - 1) % 3) WHEN 0 THEN 'REMOTE' WHEN 1 THEN 'HYBRID' ELSE 'ONSITE' END AS remote_mode,
       CASE ((series - 1) % 4) WHEN 0 THEN 'FULL_TIME' WHEN 1 THEN 'CONTRACT'
            WHEN 2 THEN 'PART_TIME' ELSE 'INTERNSHIP' END AS employment_type,
       CASE ((series - 1) % 5) WHEN 0 THEN 'JUNIOR' WHEN 1 THEN 'MID' WHEN 2 THEN 'SENIOR'
            WHEN 3 THEN 'LEAD' ELSE 'INTERN' END AS seniority,
       18000 + ((series % 80) * 1000) AS salary_min,
       28000 + ((series % 80) * 1000) AS salary_max,
       now() - ((series % 180) * interval '1 day') - ((series % 86400) * interval '1 second') AS published_at,
       CASE ((series - 1) % 5) WHEN 0 THEN 'Ciudad de México' WHEN 1 THEN 'Guadalajara'
            WHEN 2 THEN 'Monterrey' WHEN 3 THEN 'Querétaro' ELSE 'Puebla' END AS city_name,
       CASE ((series - 1) % 5) WHEN 0 THEN 'Ciudad de México' WHEN 1 THEN 'Jalisco'
            WHEN 2 THEN 'Nuevo León' WHEN 3 THEN 'Querétaro' ELSE 'Puebla' END AS state_name
FROM generate_series(1, LEAST(:fixture_count::bigint, 1000000)) series
CROSS JOIN role_count
JOIN selectable_roles role ON role.role_no=((series - 1) % role_count.value) + 1;

INSERT INTO jobs.employer(public_id, canonical_name, name_normalized)
SELECT md5('employer:' || employer_no)::uuid,
       'Empresa Fixture ' || lpad(employer_no::text, 3, '0'),
       'empresa fixture ' || lpad(employer_no::text, 3, '0')
FROM (SELECT DISTINCT employer_no FROM fixture_job) employers
ON CONFLICT (name_normalized) DO NOTHING;

INSERT INTO jobs.canonical_job(public_id, identity_key, employer_id, role_family_id, title, title_normalized,
    description, seniority, remote_mode, employment_type, salary_min_monthly, salary_max_monthly, currency,
    published_at, expires_at, status)
SELECT fixture.job_public_id, fixture.identity_key, employer.id,
       role.id, fixture.title, lower(unaccent(fixture.title)),
       CASE
         WHEN fixture.role_slug IN ('java-developer','backend-developer') THEN 'Desarrollo de servicios backend con Java, Spring Boot, PostgreSQL y Git.'
         WHEN fixture.role_slug IN ('python-developer','data-scientist','data-analyst','data-engineer','machine-learning-engineer','ai-developer','business-intelligence-engineer') THEN 'Análisis de datos con Python, PostgreSQL y comunicación de resultados.'
         WHEN fixture.role_slug IN ('devops-engineer','cloud-engineer','cloud-architect','sre-engineer','system-administrator') THEN 'Automatización de plataforma con Docker, Git y prácticas DevOps.'
         WHEN fixture.role_slug IN ('frontend-developer','react-developer','angular-developer','vuejs-developer','mobile-developer','ios-developer','android-developer','flutter-developer','react-native-developer') THEN 'Construcción de interfaces accesibles y mantenibles para productos digitales.'
         ELSE 'Colaboración en productos digitales, priorización y comunicación con equipos de ingeniería.'
       END,
       fixture.seniority, fixture.remote_mode, fixture.employment_type,
       fixture.salary_min, fixture.salary_max, 'MXN', fixture.published_at,
       fixture.published_at + interval '90 days', 'ACTIVE'
FROM fixture_job fixture
JOIN jobs.employer employer ON employer.name_normalized=
    ('empresa fixture ' || lpad(fixture.employer_no::text, 3, '0'))::citext
JOIN catalog.role_family role ON role.public_id=fixture.role_public_id
ON CONFLICT (identity_key) DO UPDATE SET
    role_family_id=excluded.role_family_id, title=excluded.title, title_normalized=excluded.title_normalized, description=excluded.description,
    seniority=excluded.seniority, remote_mode=excluded.remote_mode, employment_type=excluded.employment_type,
    salary_min_monthly=excluded.salary_min_monthly, salary_max_monthly=excluded.salary_max_monthly,
    published_at=excluded.published_at, expires_at=excluded.expires_at, status='ACTIVE',
    version=jobs.canonical_job.version+1, updated_at=now();

INSERT INTO jobs.job_location(public_id, canonical_job_id, country_code, state_name, state_normalized,
    city_name, city_normalized)
SELECT md5('location:' || fixture.fixture_no)::uuid, job.id, 'MX', fixture.state_name,
       lower(unaccent(fixture.state_name)), fixture.city_name, lower(unaccent(fixture.city_name))
FROM fixture_job fixture JOIN jobs.canonical_job job ON job.identity_key=fixture.identity_key
ON CONFLICT (public_id) DO UPDATE SET state_name=excluded.state_name,
    state_normalized=excluded.state_normalized, city_name=excluded.city_name,
    city_normalized=excluded.city_normalized;

INSERT INTO jobs.source_posting(public_id, source_id, external_id, original_url, normalized_url_hash,
    payload, published_at, status)
SELECT md5('posting:' || fixture.fixture_no)::uuid, source.id, fixture.identity_key,
       'https://fixtures.jobmatch.test/jobs/' || fixture.fixture_no,
       md5('https://fixtures.jobmatch.test/jobs/' || fixture.fixture_no) ||
         md5('url:https://fixtures.jobmatch.test/jobs/' || fixture.fixture_no),
       jsonb_build_object('fixture', true, 'number', fixture.fixture_no), fixture.published_at, 'ACTIVE'
FROM fixture_job fixture CROSS JOIN jobs.source source
WHERE source.source_key='LOCAL_FIXTURES'
ON CONFLICT (source_id, external_id) WHERE external_id IS NOT NULL DO UPDATE SET
    last_seen_at=now(), published_at=excluded.published_at, status='ACTIVE', payload=excluded.payload;

INSERT INTO jobs.job_source_link(canonical_job_id, source_posting_id, link_type, preferred, active)
SELECT job.id, posting.id, 'OFFICIAL', true, true
FROM fixture_job fixture
JOIN jobs.canonical_job job ON job.identity_key=fixture.identity_key
JOIN jobs.source_posting posting ON posting.external_id=fixture.identity_key
JOIN jobs.source source ON source.id=posting.source_id AND source.source_key='LOCAL_FIXTURES'
ON CONFLICT (source_posting_id) DO UPDATE SET active=true, preferred=true;

INSERT INTO jobs.job_requirement(public_id, canonical_job_id, requirement_text, category, mandatory, position)
SELECT md5('requirement:' || fixture.fixture_no)::uuid, job.id,
       CASE
            WHEN fixture.role_slug IN ('java-developer','backend-developer') THEN 'Experiencia construyendo servicios backend.'
            WHEN fixture.role_slug IN ('python-developer','data-scientist','data-analyst','data-engineer','machine-learning-engineer','ai-developer','business-intelligence-engineer') THEN 'Capacidad para analizar y explicar datos.'
            WHEN fixture.role_slug IN ('devops-engineer','cloud-engineer','cloud-architect','sre-engineer','system-administrator') THEN 'Experiencia automatizando infraestructura.'
            ELSE 'Experiencia colaborando en productos digitales.' END,
       'EXPERIENCE', true, 1
FROM fixture_job fixture JOIN jobs.canonical_job job ON job.identity_key=fixture.identity_key
ON CONFLICT (public_id) DO UPDATE SET requirement_text=excluded.requirement_text;

INSERT INTO jobs.job_skill_requirement(public_id, canonical_job_id, skill_id, priority, evidence_text)
SELECT md5('skill-requirement:' || fixture.fixture_no)::uuid, job.id, skill.id, 'REQUIRED',
       'Mencionada explícitamente en la descripción.'
FROM fixture_job fixture
JOIN jobs.canonical_job job ON job.identity_key=fixture.identity_key
JOIN catalog.skill skill ON skill.public_id=(CASE
    WHEN fixture.role_slug IN ('java-developer','backend-developer') THEN '12000000-0000-0000-0000-000000000001'
    WHEN fixture.role_slug IN ('python-developer','data-scientist','data-analyst','data-engineer','machine-learning-engineer','ai-developer','business-intelligence-engineer') THEN '12000000-0000-0000-0000-000000000006'
    WHEN fixture.role_slug IN ('devops-engineer','cloud-engineer','cloud-architect','sre-engineer','system-administrator') THEN '12000000-0000-0000-0000-000000000007'
    WHEN fixture.role_slug IN ('frontend-developer','react-developer','angular-developer','vuejs-developer','mobile-developer','ios-developer','android-developer','flutter-developer','react-native-developer') THEN '12000000-0000-0000-0000-000000000004'
    ELSE '12000000-0000-0000-0000-000000000008' END)::uuid
ON CONFLICT (public_id) DO UPDATE SET skill_id=excluded.skill_id, evidence_text=excluded.evidence_text;

INSERT INTO jobs.job_search_document(canonical_job_id, role_family_id, country_code, state_normalized,
    city_normalized, remote_mode, employment_type, salary_max_monthly, search_vector, projected_job_version)
SELECT job.id, job.role_family_id, location.country_code, location.state_normalized, location.city_normalized,
       job.remote_mode, job.employment_type, job.salary_max_monthly,
       setweight(to_tsvector('spanish', unaccent(job.title)), 'A') ||
       setweight(to_tsvector('spanish', unaccent(employer.canonical_name)), 'B') ||
       setweight(to_tsvector('spanish', unaccent(job.description)), 'C') ||
       setweight(to_tsvector('spanish', unaccent(coalesce(string_agg(skill.display_name, ' '), ''))), 'B'),
       job.version
FROM fixture_job fixture
JOIN jobs.canonical_job job ON job.identity_key=fixture.identity_key
JOIN jobs.employer employer ON employer.id=job.employer_id
JOIN jobs.job_location location ON location.canonical_job_id=job.id
LEFT JOIN jobs.job_skill_requirement requirement ON requirement.canonical_job_id=job.id
LEFT JOIN catalog.skill skill ON skill.id=requirement.skill_id
GROUP BY job.id, location.country_code, location.state_normalized, location.city_normalized, employer.canonical_name
ON CONFLICT (canonical_job_id) DO UPDATE SET
    role_family_id=excluded.role_family_id, country_code=excluded.country_code,
    state_normalized=excluded.state_normalized, city_normalized=excluded.city_normalized,
    remote_mode=excluded.remote_mode, employment_type=excluded.employment_type,
    salary_max_monthly=excluded.salary_max_monthly, search_vector=excluded.search_vector,
    projected_job_version=excluded.projected_job_version, updated_at=now();

COMMIT;

ANALYZE jobs.canonical_job;
ANALYZE jobs.job_search_document;
