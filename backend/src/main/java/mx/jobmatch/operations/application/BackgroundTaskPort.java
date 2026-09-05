package mx.jobmatch.operations.application;

import mx.jobmatch.operations.domain.BackgroundTask;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface BackgroundTaskPort {
    BackgroundTask enqueue(String type, String payload, String deduplicationKey, Instant availableAt);
    Optional<BackgroundTask> find(UUID publicId);
    Optional<BackgroundTask> claimNext(String workerId, Duration lease);
    void complete(UUID publicId, String workerId);
    void retry(UUID publicId, String workerId, String safeErrorCode, Instant availableAt);
}

