package mx.jobmatch.ingestion.domain;

import java.util.UUID;

public record ConnectorQuery(UUID id, String roleQuery, String locationQuery, String cursor,
                             String boardKey, String employerName, String countryCode) {
    public ConnectorQuery(UUID id, String roleQuery, String locationQuery, String cursor) {
        this(id, roleQuery, locationQuery, cursor, null, null, null);
    }
}
