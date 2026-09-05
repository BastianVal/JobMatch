CREATE TABLE tracking.job_impression (
    account_id bigint NOT NULL REFERENCES iam.account(id) ON DELETE CASCADE,
    canonical_job_id bigint NOT NULL REFERENCES jobs.canonical_job(id) ON DELETE CASCADE,
    first_viewed_at timestamptz NOT NULL DEFAULT now(),
    last_viewed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, canonical_job_id)
);
CREATE INDEX job_impression_recent_idx
    ON tracking.job_impression(account_id, last_viewed_at DESC, canonical_job_id);

CREATE TABLE tracking.user_job (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    account_id bigint NOT NULL REFERENCES iam.account(id) ON DELETE CASCADE,
    canonical_job_id bigint NOT NULL REFERENCES jobs.canonical_job(id) ON DELETE CASCADE,
    state varchar(20) NOT NULL CHECK (state IN
        ('SAVED','DISCARDED','APPLIED','INTERVIEW','OFFER','ACCEPTED','REJECTED','WITHDRAWN')),
    note varchar(2000),
    version bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (account_id, canonical_job_id)
);
CREATE INDEX user_job_owner_state_idx
    ON tracking.user_job(account_id, state, updated_at DESC, id DESC);

CREATE TABLE tracking.user_job_event (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    user_job_id bigint NOT NULL REFERENCES tracking.user_job(id) ON DELETE CASCADE,
    from_state varchar(20),
    to_state varchar(20) NOT NULL,
    note_snapshot varchar(2000),
    resulting_version bigint NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    CHECK (from_state IS NULL OR from_state IN
        ('SAVED','DISCARDED','APPLIED','INTERVIEW','OFFER','ACCEPTED','REJECTED','WITHDRAWN')),
    CHECK (to_state IN
        ('SAVED','DISCARDED','APPLIED','INTERVIEW','OFFER','ACCEPTED','REJECTED','WITHDRAWN')),
    UNIQUE (user_job_id, resulting_version)
);
CREATE INDEX user_job_event_history_idx ON tracking.user_job_event(user_job_id, occurred_at, id);
