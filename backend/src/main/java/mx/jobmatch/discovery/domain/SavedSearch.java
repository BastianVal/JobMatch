package mx.jobmatch.discovery.domain;

import java.time.Instant;
import java.util.UUID;

public record SavedSearch(UUID id, String name, JobSearchCriteria criteria, long version,
                          Instant createdAt, Instant updatedAt) {}
