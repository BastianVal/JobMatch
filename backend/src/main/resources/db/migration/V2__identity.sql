CREATE TABLE iam.account (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    email citext NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    status varchar(30) NOT NULL CHECK (status IN ('PENDING_VERIFICATION','ACTIVE','DELETION_PENDING')),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    verified_at timestamptz
);

CREATE TABLE iam.email_token (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    account_id bigint NOT NULL REFERENCES iam.account(id) ON DELETE CASCADE,
    purpose varchar(30) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL','RESET_PASSWORD')),
    token_hash char(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX email_token_account_idx ON iam.email_token(account_id, purpose, created_at DESC);

CREATE TABLE iam.auth_attempt (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email_hash char(64) NOT NULL,
    ip_hash char(64) NOT NULL,
    result varchar(20) NOT NULL CHECK (result IN ('SUCCESS','FAILURE','BLOCKED')),
    attempted_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX auth_attempt_email_window_idx ON iam.auth_attempt(email_hash, attempted_at DESC) WHERE result <> 'SUCCESS';
CREATE INDEX auth_attempt_ip_window_idx ON iam.auth_attempt(ip_hash, attempted_at DESC) WHERE result <> 'SUCCESS';

CREATE TABLE iam.spring_session (
    primary_id char(36) NOT NULL PRIMARY KEY,
    session_id char(36) NOT NULL UNIQUE,
    creation_time bigint NOT NULL,
    last_access_time bigint NOT NULL,
    max_inactive_interval integer NOT NULL,
    expiry_time bigint NOT NULL,
    principal_name varchar(100)
);
CREATE INDEX spring_session_expiry_idx ON iam.spring_session(expiry_time);
CREATE INDEX spring_session_principal_idx ON iam.spring_session(principal_name);

CREATE TABLE iam.spring_session_attributes (
    session_primary_id char(36) NOT NULL REFERENCES iam.spring_session(primary_id) ON DELETE CASCADE,
    attribute_name varchar(200) NOT NULL,
    attribute_bytes bytea NOT NULL,
    PRIMARY KEY (session_primary_id, attribute_name)
);
