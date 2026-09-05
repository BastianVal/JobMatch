package mx.jobmatch.ingestion.application;

import mx.jobmatch.ingestion.domain.ConnectorPage;
import mx.jobmatch.ingestion.domain.ConnectorQuery;

public interface ConnectorPort {
    String sourceKey();
    ConnectorPage fetch(ConnectorQuery query);
}
