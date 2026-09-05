package mx.jobmatch.operations.application;

import java.util.UUID;

public interface OutboxPort {
    UUID append(String aggregateType, UUID aggregatePublicId, String eventType, String payload);
}

