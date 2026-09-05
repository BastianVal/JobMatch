CREATE SCHEMA cvimport;

CREATE TABLE cvimport.stored_file (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    account_id bigint NOT NULL REFERENCES iam.account(id) ON DELETE CASCADE,
    storage_key varchar(100) NOT NULL UNIQUE,
    detected_media_type varchar(120) NOT NULL,
    sha256 char(64) NOT NULL,
    byte_size bigint NOT NULL CHECK (byte_size BETWEEN 1 AND 5242880),
    status varchar(20) NOT NULL CHECK (status IN ('STORED','DELETED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
CREATE UNIQUE INDEX stored_file_active_hash_idx ON cvimport.stored_file(account_id, sha256) WHERE status='STORED';

CREATE TABLE cvimport.cv_document (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    account_id bigint NOT NULL REFERENCES iam.account(id) ON DELETE CASCADE,
    stored_file_id bigint NOT NULL UNIQUE REFERENCES cvimport.stored_file(id) ON DELETE CASCADE,
    original_filename varchar(255) NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('ACTIVE','DELETED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
CREATE INDEX cv_document_owner_idx ON cvimport.cv_document(account_id, created_at DESC);

CREATE TABLE cvimport.import_run (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    cv_document_id bigint NOT NULL REFERENCES cvimport.cv_document(id) ON DELETE CASCADE,
    profile_base_version bigint NOT NULL CHECK (profile_base_version >= 0),
    status varchar(20) NOT NULL CHECK (status IN ('QUEUED','PROCESSING','READY','CONFIRMED','FAILED','EXPIRED')),
    extractor_version varchar(50) NOT NULL,
    safe_error_code varchar(100),
    extracted_text_hash char(64),
    confirmed_profile_version bigint,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    finished_at timestamptz,
    confirmed_at timestamptz
);
CREATE INDEX import_run_document_idx ON cvimport.import_run(cv_document_id, created_at DESC);

CREATE TABLE cvimport.import_candidate (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    import_run_id bigint NOT NULL REFERENCES cvimport.import_run(id) ON DELETE CASCADE,
    candidate_type varchar(30) NOT NULL CHECK (candidate_type IN ('TRAJECTORY','EDUCATION','CERTIFICATION','LANGUAGE','SKILL')),
    payload_version integer NOT NULL DEFAULT 1 CHECK (payload_version > 0),
    proposed_payload jsonb NOT NULL,
    fingerprint char(64) NOT NULL,
    decision varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (decision IN ('PENDING','ACCEPTED','SKIPPED')),
    decision_version bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (import_run_id, candidate_type, fingerprint)
);

CREATE TABLE cvimport.duplicate_case (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    import_candidate_id bigint NOT NULL UNIQUE REFERENCES cvimport.import_candidate(id) ON DELETE CASCADE,
    existing_entity_type varchar(30) NOT NULL,
    existing_entity_public_id uuid NOT NULL,
    similarity_score numeric(5,4) NOT NULL CHECK (similarity_score BETWEEN 0 AND 1),
    resolution varchar(30) CHECK (resolution IN ('KEEP_BOTH','REPLACE_EXISTING','SKIP')),
    resolved_at timestamptz
);
