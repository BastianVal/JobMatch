package mx.jobmatch.operations.domain;

import java.time.Instant;
import java.util.UUID;

public record BackgroundTask(
        UUID publicId,
        String type,
        String payload,
        String deduplicationKey,
        Status status,
        int attempts,
        Instant availableAt) {
    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }
}

