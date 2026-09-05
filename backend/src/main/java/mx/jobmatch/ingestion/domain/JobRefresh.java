package mx.jobmatch.ingestion.domain;

import java.time.Instant;
import java.util.UUID;

public record JobRefresh(UUID id, String status, int scheduledConnectors, int completedConnectors,
                         int failedConnectors, Instant nextAllowedAt, Instant createdAt) {}
