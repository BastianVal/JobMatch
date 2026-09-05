package mx.jobmatch.ingestion.adapters.worker;

import mx.jobmatch.ingestion.application.IngestionRepository;
import mx.jobmatch.operations.application.BackgroundTaskPort;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Profile("worker")
@Component
public class ConnectorQueryScheduler {
    private final IngestionRepository repository;
    private final BackgroundTaskPort tasks;

    public ConnectorQueryScheduler(IngestionRepository repository, BackgroundTaskPort tasks) {
        this.repository = repository;
        this.tasks = tasks;
    }

    @Scheduled(fixedDelayString = "${jobmatch.ingestion.schedule-delay-ms:60000}")
    @Transactional
    public void scheduleDueQueries() {
        Instant now = Instant.now();
        for (var query : repository.findDueQueries(50)) {
            long jitter = Math.floorMod(query.query().id().hashCode(), 1800);
            repository.postponeQuery(query.query().id(), now.plusSeconds(6 * 3600L + jitter));
            String payload = "{\"queryId\":\"" + query.query().id() + "\",\"refreshId\":null}";
            tasks.enqueue("SYNC_CONNECTOR", payload,
                    query.sourceKey() + ":scheduled:" + query.query().id() + ":" + now.getEpochSecond() / 21600,
                    now, 3);
        }
    }
}
