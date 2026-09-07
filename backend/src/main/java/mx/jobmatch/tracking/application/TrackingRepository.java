package mx.jobmatch.tracking.application;

import mx.jobmatch.tracking.domain.JobActivity;
import mx.jobmatch.tracking.domain.TrackedJob;
import mx.jobmatch.tracking.domain.TrackingState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrackingRepository {
    int recordImpressions(UUID accountId, List<UUID> jobIds);
    List<JobActivity> activities(UUID accountId, List<UUID> jobIds);
    Optional<TrackedJob> find(UUID accountId, UUID jobId);
    Optional<TrackedJob> findById(UUID accountId, UUID trackingId);
    List<TrackedJob> list(UUID accountId, List<TrackingState> states, int limit);
    TrackedJob create(UUID accountId, UUID jobId, TrackingState state, String note);
    Optional<TrackedJob> update(UUID accountId, UUID jobId, long expectedVersion,
                                TrackingState fromState, TrackingState targetState, String note);
    void deleteForAccount(UUID accountId);
}
