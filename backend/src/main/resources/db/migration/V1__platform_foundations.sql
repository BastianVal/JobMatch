CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE SCHEMA IF NOT EXISTS iam;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS profile;
CREATE SCHEMA IF NOT EXISTS cv;
CREATE SCHEMA IF NOT EXISTS jobs;
CREATE SCHEMA IF NOT EXISTS ingestion;
CREATE SCHEMA IF NOT EXISTS matching;
CREATE SCHEMA IF NOT EXISTS tracking;
CREATE SCHEMA IF NOT EXISTS ops;

CREATE TABLE ops.background_task (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    task_type varchar(100) NOT NULL,
    payload jsonb NOT NULL,
    deduplication_key varchar(200) NOT NULL,
    priority integer NOT NULL DEFAULT 0,
    status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
    attempts integer NOT NULL DEFAULT 0,
    max_attempts integer NOT NULL DEFAULT 5,
    available_at timestamptz NOT NULL DEFAULT now(),
    leased_until timestamptz,
    leased_by varchar(200),
    last_error_code varchar(100),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (task_type, deduplication_key)
);

CREATE INDEX background_task_claim_idx ON ops.background_task (available_at, priority DESC, id)
WHERE status = 'PENDING';

CREATE TABLE ops.outbox_event (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    aggregate_type varchar(100) NOT NULL,
    aggregate_public_id uuid NOT NULL,
    event_type varchar(150) NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz
);

CREATE INDEX outbox_event_pending_idx ON ops.outbox_event (occurred_at, id) WHERE published_at IS NULL;

CREATE TABLE ops.idempotency_record (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operation varchar(100) NOT NULL,
    idempotency_key varchar(200) NOT NULL,
    request_hash char(64) NOT NULL,
    resource_public_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    UNIQUE (operation, idempotency_key)
);

