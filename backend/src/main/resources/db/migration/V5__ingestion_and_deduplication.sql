ALTER TABLE jobs.source
    ADD COLUMN complete_snapshot_supported boolean NOT NULL DEFAULT false,
    ADD COLUMN quota_per_day integer CHECK (quota_per_day IS NULL OR quota_per_day > 0);

INSERT INTO jobs.source(public_id, source_key, display_name, complete_snapshot_supported, quota_per_day)
VALUES
 ('21000000-0000-0000-0000-000000000001','JOOBLE','Jooble',false,500),
 ('21000000-0000-0000-0000-000000000002','ADZUNA','Adzuna',true,1000),
 ('21000000-0000-0000-0000-000000000003','GREENHOUSE','Greenhouse',true,2000),
 ('21000000-0000-0000-0000-000000000004','LEVER','Lever',true,2000),
 ('21000000-0000-0000-0000-000000000005','ASHBY','Ashby',true,2000);

CREATE TABLE ingestion.connector_query (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    source_id bigint NOT NULL REFERENCES jobs.source(id),
    query_signature char(64) NOT NULL,
    role_query varchar(200),
    location_query varchar(200),
    enabled boolean NOT NULL DEFAULT true,
    next_scheduled_at timestamptz NOT NULL DEFAULT now(),
    last_requested_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (source_id, query_signature)
);
CREATE INDEX connector_query_schedule_idx ON ingestion.connector_query(next_scheduled_at, id) WHERE enabled;

CREATE TABLE ingestion.query_demand (
    connector_query_id bigint NOT NULL REFERENCES ingestion.connector_query(id) ON DELETE CASCADE,
    account_id bigint NOT NULL REFERENCES iam.account(id) ON DELETE CASCADE,
    role_family_id bigint REFERENCES catalog.role_family(id),
    last_demanded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (connector_query_id, account_id)
);

CREATE TABLE ingestion.job_refresh (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    account_id bigint NOT NULL REFERENCES iam.account(id) ON DELETE CASCADE,
    request_signature char(64) NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('SCHEDULED','COOLDOWN','COMPLETED','PARTIAL','FAILED')),
    scheduled_connectors integer NOT NULL DEFAULT 0,
    completed_connectors integer NOT NULL DEFAULT 0,
    failed_connectors integer NOT NULL DEFAULT 0,
    next_allowed_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX job_refresh_owner_idx ON ingestion.job_refresh(account_id, created_at DESC);

CREATE TABLE ingestion.sync_run (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    connector_query_id bigint NOT NULL REFERENCES ingestion.connector_query(id),
    refresh_id bigint REFERENCES ingestion.job_refresh(id) ON DELETE SET NULL,
    status varchar(20) NOT NULL CHECK (status IN ('RUNNING','COMPLETED','FAILED')),
    cursor varchar(1000),
    fetched_count integer NOT NULL DEFAULT 0,
    created_count integer NOT NULL DEFAULT 0,
    updated_count integer NOT NULL DEFAULT 0,
    linked_count integer NOT NULL DEFAULT 0,
    kept_separate_count integer NOT NULL DEFAULT 0,
    error_count integer NOT NULL DEFAULT 0,
    complete_response boolean NOT NULL DEFAULT false,
    safe_error_code varchar(100),
    started_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz
);
CREATE INDEX sync_run_query_idx ON ingestion.sync_run(connector_query_id, started_at DESC);

CREATE TABLE ingestion.sync_item_error (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sync_run_id bigint NOT NULL REFERENCES ingestion.sync_run(id) ON DELETE CASCADE,
    source_reference_hash char(64),
    safe_error_code varchar(100) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ingestion.source_payload (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id bigint NOT NULL REFERENCES jobs.source(id),
    source_posting_id bigint REFERENCES jobs.source_posting(id) ON DELETE CASCADE,
    payload_hash char(64) NOT NULL,
    payload jsonb NOT NULL,
    observed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX source_payload_retention_idx ON ingestion.source_payload(observed_at, id);

CREATE TABLE ingestion.source_runtime_state (
    source_id bigint PRIMARY KEY REFERENCES jobs.source(id) ON DELETE CASCADE,
    quota_window_start date NOT NULL DEFAULT current_date,
    requests_in_window integer NOT NULL DEFAULT 0,
    consecutive_failures integer NOT NULL DEFAULT 0,
    circuit_open_until timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE jobs.job_merge_audit (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_posting_id bigint NOT NULL REFERENCES jobs.source_posting(id) ON DELETE CASCADE,
    candidate_job_id bigint REFERENCES jobs.canonical_job(id) ON DELETE SET NULL,
    resolved_job_id bigint NOT NULL REFERENCES jobs.canonical_job(id) ON DELETE CASCADE,
    decision varchar(30) NOT NULL CHECK (decision IN ('EXACT','AUTO_MERGED','KEPT_SEPARATE')),
    similarity_score numeric(5,4),
    algorithm_version varchar(30) NOT NULL,
    reasons jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX job_merge_audit_posting_idx ON jobs.job_merge_audit(source_posting_id, created_at DESC);
