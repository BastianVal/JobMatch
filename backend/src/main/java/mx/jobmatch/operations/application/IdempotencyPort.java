package mx.jobmatch.operations.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyPort {
    Optional<Record> find(String operation, String key);
    void save(String operation, String key, String requestHash, UUID resourcePublicId, Instant expiresAt);

    record Record(String requestHash, UUID resourcePublicId) {}
}

