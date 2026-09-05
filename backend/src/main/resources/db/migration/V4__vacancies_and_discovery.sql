CREATE TABLE jobs.source (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    source_key varchar(80) NOT NULL UNIQUE,
    display_name varchar(120) NOT NULL,
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE jobs.employer (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    canonical_name varchar(200) NOT NULL,
    name_normalized citext NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE jobs.employer_alias (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employer_id bigint NOT NULL REFERENCES jobs.employer(id) ON DELETE CASCADE,
    alias citext NOT NULL,
    alias_normalized citext NOT NULL,
    UNIQUE (employer_id, alias_normalized)
);
CREATE INDEX employer_alias_lookup_idx ON jobs.employer_alias(alias_normalized);

CREATE TABLE jobs.canonical_job (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    identity_key varchar(200) NOT NULL UNIQUE,
    employer_id bigint NOT NULL REFERENCES jobs.employer(id),
    role_family_id bigint REFERENCES catalog.role_family(id),
    title varchar(240) NOT NULL,
    title_normalized varchar(240) NOT NULL,
    description text NOT NULL,
    seniority varchar(30),
    remote_mode varchar(20) NOT NULL CHECK (remote_mode IN ('REMOTE','HYBRID','ONSITE')),
    employment_type varchar(20) NOT NULL CHECK (employment_type IN ('FULL_TIME','PART_TIME','CONTRACT','INTERNSHIP')),
    salary_min_monthly numeric(12,2),
    salary_max_monthly numeric(12,2),
    currency char(3),
    published_at timestamptz NOT NULL,
    expires_at timestamptz,
    status varchar(30) NOT NULL CHECK (status IN ('ACTIVE','CLOSED','SUSPECTED_EXPIRED')),
    version bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (seniority IS NULL OR seniority IN ('INTERN','JUNIOR','MID','SENIOR','LEAD','MANAGER','DIRECTOR')),
    CHECK ((salary_min_monthly IS NULL AND salary_max_monthly IS NULL AND currency IS NULL) OR
           (salary_min_monthly IS NOT NULL AND salary_max_monthly IS NOT NULL AND currency IS NOT NULL
            AND salary_min_monthly >= 0 AND salary_max_monthly >= salary_min_monthly))
);
CREATE INDEX canonical_job_active_date_idx ON jobs.canonical_job(published_at DESC, public_id DESC)
WHERE status='ACTIVE';
CREATE INDEX canonical_job_role_active_idx ON jobs.canonical_job(role_family_id, published_at DESC, public_id DESC)
WHERE status='ACTIVE';
CREATE INDEX canonical_job_employer_idx ON jobs.canonical_job(employer_id);
CREATE INDEX canonical_job_title_trgm_idx ON jobs.canonical_job USING gin(title_normalized gin_trgm_ops);

CREATE TABLE jobs.job_location (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    canonical_job_id bigint NOT NULL REFERENCES jobs.canonical_job(id) ON DELETE CASCADE,
    country_code char(2) NOT NULL,
    state_name varchar(120),
    state_normalized varchar(120),
    city_name varchar(160),
    city_normalized varchar(160),
    UNIQUE (canonical_job_id, country_code, state_normalized, city_normalized)
);
CREATE INDEX job_location_filter_idx ON jobs.job_location(country_code, state_normalized, city_normalized, canonical_job_id);

CREATE TABLE jobs.source_posting (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    source_id bigint NOT NULL REFERENCES jobs.source(id),
    external_id varchar(300),
    original_url varchar(2000) NOT NULL,
    normalized_url_hash char(64) NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    first_seen_at timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz,
    status varchar(20) NOT NULL CHECK (status IN ('ACTIVE','CLOSED','MISSING'))
);
CREATE UNIQUE INDEX source_posting_external_unique_idx ON jobs.source_posting(source_id, external_id)
WHERE external_id IS NOT NULL;
CREATE UNIQUE INDEX source_posting_url_unique_idx ON jobs.source_posting(source_id, normalized_url_hash);

CREATE TABLE jobs.job_source_link (
    canonical_job_id bigint NOT NULL REFERENCES jobs.canonical_job(id) ON DELETE CASCADE,
    source_posting_id bigint NOT NULL UNIQUE REFERENCES jobs.source_posting(id) ON DELETE CASCADE,
    link_type varchar(20) NOT NULL CHECK (link_type IN ('OFFICIAL','AGGREGATOR')),
    preferred boolean NOT NULL DEFAULT false,
    active boolean NOT NULL DEFAULT true,
    PRIMARY KEY (canonical_job_id, source_posting_id)
);
CREATE UNIQUE INDEX job_preferred_link_unique_idx ON jobs.job_source_link(canonical_job_id)
WHERE preferred AND active;

CREATE TABLE jobs.job_requirement (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    canonical_job_id bigint NOT NULL REFERENCES jobs.canonical_job(id) ON DELETE CASCADE,
    requirement_text varchar(1000) NOT NULL,
    category varchar(30) NOT NULL CHECK (category IN ('EXPERIENCE','EDUCATION','LANGUAGE','RESPONSIBILITY','OTHER')),
    mandatory boolean NOT NULL DEFAULT false,
    position smallint NOT NULL DEFAULT 0
);
CREATE INDEX job_requirement_job_idx ON jobs.job_requirement(canonical_job_id, position);

CREATE TABLE jobs.job_skill_requirement (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    canonical_job_id bigint NOT NULL REFERENCES jobs.canonical_job(id) ON DELETE CASCADE,
    skill_id bigint NOT NULL REFERENCES catalog.skill(id),
    priority varchar(20) NOT NULL CHECK (priority IN ('REQUIRED','DESIRED','RESPONSIBILITY','UNCLEAR')),
    evidence_text varchar(500),
    UNIQUE (canonical_job_id, skill_id, priority)
);
CREATE INDEX job_skill_requirement_job_idx ON jobs.job_skill_requirement(canonical_job_id, priority);
CREATE INDEX job_skill_requirement_skill_idx ON jobs.job_skill_requirement(skill_id, canonical_job_id);

CREATE TABLE jobs.job_search_document (
    canonical_job_id bigint PRIMARY KEY REFERENCES jobs.canonical_job(id) ON DELETE CASCADE,
    role_family_id bigint REFERENCES catalog.role_family(id),
    country_code char(2) NOT NULL,
    state_normalized varchar(120),
    city_normalized varchar(160),
    remote_mode varchar(20) NOT NULL,
    employment_type varchar(20) NOT NULL,
    salary_max_monthly numeric(12,2),
    search_vector tsvector NOT NULL,
    projected_job_version bigint NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX job_search_vector_idx ON jobs.job_search_document USING gin(search_vector);
CREATE INDEX job_search_filters_idx ON jobs.job_search_document
    (country_code, remote_mode, employment_type, role_family_id, city_normalized);
CREATE INDEX job_search_salary_idx ON jobs.job_search_document(salary_max_monthly)
WHERE salary_max_monthly IS NOT NULL;

CREATE TABLE profile.saved_search (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    account_id bigint NOT NULL REFERENCES iam.account(id) ON DELETE CASCADE,
    name varchar(120) NOT NULL,
    criteria jsonb NOT NULL,
    version bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (account_id, name)
);
CREATE INDEX saved_search_owner_idx ON profile.saved_search(account_id, updated_at DESC, id DESC);

INSERT INTO jobs.source(public_id, source_key, display_name)
VALUES ('20000000-0000-0000-0000-000000000001', 'LOCAL_FIXTURES', 'Fixtures locales');
