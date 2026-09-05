package mx.jobmatch.tracking.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TrackedJob(UUID id, UUID jobId, String title, String employer, TrackingState state,
                         String note, long version, Instant updatedAt, List<Event> history) {
    public TrackedJob { history = history == null ? List.of() : List.copyOf(history); }
    public record Event(UUID id, TrackingState fromState, TrackingState toState, String note,
                        long resultingVersion, Instant occurredAt) {}
}
