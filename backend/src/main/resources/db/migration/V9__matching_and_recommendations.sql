CREATE TABLE matching.job_fact_snapshot (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    canonical_job_id bigint NOT NULL REFERENCES jobs.canonical_job(id) ON DELETE CASCADE,
    job_version bigint NOT NULL,
    catalog_version integer NOT NULL,
    extractor_version varchar(50) NOT NULL,
    facts jsonb NOT NULL,
    extracted_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (canonical_job_id, job_version, catalog_version, extractor_version)
);

CREATE TABLE matching.match_result (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    profile_id bigint NOT NULL REFERENCES profile.professional_profile(id) ON DELETE CASCADE,
    canonical_job_id bigint NOT NULL REFERENCES jobs.canonical_job(id) ON DELETE CASCADE,
    profile_version bigint NOT NULL,
    job_version bigint NOT NULL,
    catalog_version integer NOT NULL,
    scoring_version varchar(50) NOT NULL,
    score numeric(5,2) NOT NULL CHECK (score BETWEEN 0 AND 100),
    classification varchar(20) NOT NULL CHECK (classification IN ('EXCELLENT','STRONG','POSSIBLE','LOW')),
    component_scores jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (profile_id, canonical_job_id, profile_version, job_version, catalog_version, scoring_version)
);
CREATE INDEX match_result_ranking_idx ON matching.match_result(profile_id, profile_version, score DESC, canonical_job_id);

CREATE TABLE matching.match_reason (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    match_result_id bigint NOT NULL REFERENCES matching.match_result(id) ON DELETE CASCADE,
    component varchar(30) NOT NULL,
    reason_type varchar(20) NOT NULL CHECK (reason_type IN ('MATCH','GAP','CONSIDERATION')),
    requirement_public_id uuid,
    requirement_snapshot jsonb NOT NULL,
    evidence_snapshot jsonb NOT NULL,
    points numeric(5,2) NOT NULL,
    explanation varchar(500) NOT NULL,
    position smallint NOT NULL,
    UNIQUE (match_result_id, position)
);

CREATE TABLE matching.recommendation (
    profile_id bigint NOT NULL REFERENCES profile.professional_profile(id) ON DELETE CASCADE,
    profile_version bigint NOT NULL,
    match_result_id bigint NOT NULL REFERENCES matching.match_result(id) ON DELETE CASCADE,
    rank integer NOT NULL CHECK (rank BETWEEN 1 AND 500),
    generated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (profile_id, profile_version, rank),
    UNIQUE (profile_id, profile_version, match_result_id)
);
