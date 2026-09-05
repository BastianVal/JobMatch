package mx.jobmatch.tracking.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record JobActivity(UUID jobId, @JsonProperty("new") boolean isNew, Instant firstViewedAt, Instant lastViewedAt,
                          UUID trackingId, TrackingState state, String note, Long version, Instant trackingUpdatedAt) {}
