package mx.jobmatch.ingestion.application;

import mx.jobmatch.ingestion.domain.ConnectorQuery;
import mx.jobmatch.ingestion.domain.JobRefresh;
import mx.jobmatch.ingestion.domain.NormalizedPosting;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IngestionRepository {
    Schedule schedule(UUID accountId, UUID roleFamilyId, String roleQuery, String locationQuery, Instant now);
    Optional<JobRefresh> findRefresh(UUID accountId, UUID refreshId);
    Optional<QuerySource> findQuery(UUID queryId);
    List<QuerySource> findDueQueries(int limit);
    void postponeQuery(UUID queryId, Instant nextScheduledAt);
    UUID startRun(UUID queryId, UUID refreshId);
    boolean acquireRequestPermit(String sourceKey, UUID queryId, UUID leaseOwner, Instant now);
    void releaseRequestPermit(String sourceKey, UUID queryId, UUID leaseOwner);
    void sourceSucceeded(String sourceKey);
    void sourceFailed(String sourceKey, Instant now);
    void finishRun(UUID runId, Counts counts, boolean completeResponse);
    void failRun(UUID runId, String safeCode);
    void itemFailed(UUID runId, String referenceHash, String safeCode);
    void connectorCompleted(UUID refreshId);
    void connectorFailed(UUID refreshId);
    UpsertResult upsert(UUID runId, NormalizedPosting posting);

    record Schedule(JobRefresh refresh, List<QuerySource> queries) {}
    record QuerySource(ConnectorQuery query, String sourceKey) {}
    record Counts(int fetched, int created, int updated, int linked, int keptSeparate, int errors) {
        public Counts add(UpsertResult result) {
            return new Counts(fetched + 1, created + (result.outcome()==Outcome.CREATED?1:0),
                    updated + (result.outcome()==Outcome.UPDATED?1:0), linked + (result.outcome()==Outcome.LINKED?1:0),
                    keptSeparate + (result.outcome()==Outcome.KEPT_SEPARATE?1:0), errors);
        }
        public Counts error() { return new Counts(fetched + 1, created, updated, linked, keptSeparate, errors + 1); }
    }
    record UpsertResult(UUID jobId, Outcome outcome) {}
    enum Outcome { CREATED, UPDATED, LINKED, KEPT_SEPARATE }
}
