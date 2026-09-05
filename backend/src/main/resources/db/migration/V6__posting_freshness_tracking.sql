CREATE TABLE ingestion.query_posting_observation (
    connector_query_id bigint NOT NULL REFERENCES ingestion.connector_query(id) ON DELETE CASCADE,
    source_posting_id bigint NOT NULL REFERENCES jobs.source_posting(id) ON DELETE CASCADE,
    last_seen_run_id bigint REFERENCES ingestion.sync_run(id) ON DELETE SET NULL,
    first_seen_at timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    consecutive_absences integer NOT NULL DEFAULT 0 CHECK (consecutive_absences >= 0),
    PRIMARY KEY (connector_query_id, source_posting_id)
);

CREATE INDEX query_posting_freshness_idx
    ON ingestion.query_posting_observation(connector_query_id, consecutive_absences, last_seen_at);
