ALTER TABLE ingestion.connector_query
    ADD COLUMN execution_lease_owner uuid,
    ADD COLUMN execution_lease_until timestamptz;

ALTER TABLE ingestion.source_runtime_state
    ADD COLUMN execution_lease_owner uuid,
    ADD COLUMN execution_lease_until timestamptz;

CREATE INDEX connector_query_execution_lease_idx
    ON ingestion.connector_query(execution_lease_until)
    WHERE execution_lease_until IS NOT NULL;
