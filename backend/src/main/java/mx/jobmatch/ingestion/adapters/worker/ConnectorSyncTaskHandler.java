package mx.jobmatch.ingestion.adapters.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.jobmatch.ingestion.application.ConnectorFailure;
import mx.jobmatch.ingestion.application.ConnectorPort;
import mx.jobmatch.ingestion.application.IngestionRepository;
import mx.jobmatch.ingestion.application.IngestionService;
import mx.jobmatch.operations.application.BackgroundTaskHandler;
import mx.jobmatch.operations.application.NonRetryableTaskException;
import mx.jobmatch.operations.domain.BackgroundTask;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Profile("worker")
@Component
public class ConnectorSyncTaskHandler implements BackgroundTaskHandler {
    private static final Logger log = LoggerFactory.getLogger(ConnectorSyncTaskHandler.class);
    private final IngestionRepository repository;
    private final IngestionService ingestion;
    private final List<ConnectorPort> connectors;
    private final ObjectMapper json;

    public ConnectorSyncTaskHandler(IngestionRepository repository, IngestionService ingestion,
                                    List<ConnectorPort> connectors, ObjectMapper json) {
        this.repository = repository;
        this.ingestion = ingestion;
        this.connectors = connectors;
        this.json = json;
    }

    @Override public boolean supports(String taskType) { return "SYNC_CONNECTOR".equals(taskType); }

    @Override
    public void handle(BackgroundTask task) {
        Payload payload = read(task.payload());
        var query = repository.findQuery(payload.queryId()).orElseThrow();
        UUID runId = repository.startRun(payload.queryId(), payload.refreshId());
        boolean permit = repository.acquireRequestPermit(query.sourceKey(), payload.queryId(), task.publicId(), Instant.now());
        if (!permit) {
            // A quota, lease or open circuit rejected this attempt before an outbound request was made.
            // Counting that as a source failure used to extend an already-open circuit indefinitely.
            repository.failRun(runId, "SOURCE_UNAVAILABLE");
            return;
        }
        try {
            ConnectorPort connector = connectors.stream().filter(item -> item.sourceKey().equals(query.sourceKey()))
                    .findFirst().orElseThrow(() -> new ConnectorFailure("CONNECTOR_NOT_CONFIGURED", false));
            var page = connector.fetch(query.query());
            ingestion.ingestPage(runId, query.sourceKey(), page);
            repository.sourceSucceeded(query.sourceKey());
            if (payload.refreshId() != null) repository.connectorCompleted(payload.refreshId());
        } catch (ConnectorFailure failure) {
            repository.sourceFailed(query.sourceKey(), Instant.now());
            repository.failRun(runId, failure.safeCode());
            if (payload.refreshId() != null && (!failure.retryable() || task.attempts() >= 3))
                repository.connectorFailed(payload.refreshId());
            if (!failure.retryable()) throw new NonRetryableTaskException(failure.safeCode());
            throw failure;
        } catch (RuntimeException failure) {
            String sqlState = failure instanceof UncategorizedSQLException sql
                    ? sql.getSQLException().getSQLState() : "n/a";
            log.warn("Connector pipeline failed: source={}, type={}, sqlState={}", query.sourceKey(),
                    failure.getClass().getSimpleName(), sqlState);
            repository.sourceFailed(query.sourceKey(), Instant.now());
            repository.failRun(runId, "CONNECTOR_PIPELINE_FAILED");
            if (payload.refreshId() != null && task.attempts() >= 3) repository.connectorFailed(payload.refreshId());
            throw failure;
        } finally {
            repository.releaseRequestPermit(query.sourceKey(), payload.queryId(), task.publicId());
        }
    }

    private Payload read(String value) {
        try { return json.readValue(value, Payload.class); }
        catch (Exception invalid) { throw new IllegalArgumentException("Invalid sync task payload"); }
    }

    public record Payload(UUID queryId, UUID refreshId) {}
}
