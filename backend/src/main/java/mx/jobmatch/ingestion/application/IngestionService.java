package mx.jobmatch.ingestion.application;

import mx.jobmatch.ingestion.domain.ConnectorPage;
import mx.jobmatch.ingestion.domain.JobRefresh;
import mx.jobmatch.ingestion.domain.PostingNormalizer;
import mx.jobmatch.operations.application.BackgroundTaskPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.UUID;

import static mx.jobmatch.ingestion.application.IngestionExceptions.InvalidRefresh;

@Service
public class IngestionService {
    private final IngestionRepository repository;
    private final BackgroundTaskPort tasks;

    public IngestionService(IngestionRepository repository, BackgroundTaskPort tasks) {
        this.repository = repository;
        this.tasks = tasks;
    }

    @Transactional
    public JobRefresh schedule(UUID accountId, UUID roleFamilyId, String roleQuery, String locationQuery) {
        String role = text(roleQuery, 200);
        String location = text(locationQuery, 200);
        if (role == null && roleFamilyId == null) throw new InvalidRefresh("Indica un rol o texto de búsqueda.");
        Instant now = Instant.now();
        var schedule = repository.schedule(accountId, roleFamilyId, role, location, now);
        long bucket = now.getEpochSecond() / 900;
        for (var query : schedule.queries()) {
            String payload = "{\"queryId\":\"" + query.query().id() + "\",\"refreshId\":\""
                    + schedule.refresh().id() + "\"}";
            tasks.enqueue("SYNC_CONNECTOR", payload, query.sourceKey() + ":" + query.query().id() + ":" + bucket,
                    now, 3);
        }
        return schedule.refresh();
    }

    @Transactional(readOnly = true)
    public JobRefresh find(UUID accountId, UUID refreshId) {
        return repository.findRefresh(accountId, refreshId).orElseThrow(IngestionExceptions.RefreshNotFound::new);
    }

    @Transactional
    public IngestionRepository.Counts ingestPage(UUID runId, String sourceKey, ConnectorPage page) {
        var counts = new IngestionRepository.Counts(0, 0, 0, 0, 0, 0);
        for (var raw : page.postings()) {
            try {
                counts = counts.add(repository.upsert(runId, PostingNormalizer.normalize(sourceKey, raw)));
            } catch (RuntimeException invalidItem) {
                String reference = PostingNormalizer.sha256(raw.externalId() == null ? "missing" : raw.externalId());
                repository.itemFailed(runId, reference, "INVALID_SOURCE_ITEM");
                counts = counts.error();
            }
        }
        repository.finishRun(runId, counts, page.completeResponse());
        return counts;
    }

    private static String text(String value, int max) {
        if (value == null) return null;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
        if (normalized.codePointCount(0, normalized.length()) > max) throw new InvalidRefresh("Un campo es demasiado largo.");
        return normalized.isEmpty() ? null : normalized;
    }
}
