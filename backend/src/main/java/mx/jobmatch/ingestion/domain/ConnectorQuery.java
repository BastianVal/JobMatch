package mx.jobmatch.ingestion.domain;

import java.util.UUID;

public record ConnectorQuery(UUID id, String roleQuery, String locationQuery, String cursor) {}
