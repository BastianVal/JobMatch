package mx.jobmatch.tracking.application;

import mx.jobmatch.operations.application.IdempotencyPort;
import mx.jobmatch.tracking.domain.TrackedJob;
import mx.jobmatch.tracking.domain.TrackingState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TrackingServiceTest {
    private final TrackingRepository tracking = mock(TrackingRepository.class);
    private final TrackingService service = new TrackingService(tracking, mock(IdempotencyPort.class));

    @Test
    void removesOnlyTheCurrentDiscardedTrackingWhenRecommendationsAreAllowedAgain() {
        UUID account = UUID.randomUUID(); UUID job = UUID.randomUUID();
        when(tracking.find(account, job)).thenReturn(Optional.of(tracked(job, TrackingState.DISCARDED, 3)));
        when(tracking.delete(account, job, 3, TrackingState.DISCARDED)).thenReturn(true);

        service.allowRecommendation(account, job, 3);

        verify(tracking).delete(account, job, 3, TrackingState.DISCARDED);
    }

    @Test
    void doesNotRemoveAJobThatIsNotDiscarded() {
        UUID account = UUID.randomUUID(); UUID job = UUID.randomUUID();
        when(tracking.find(account, job)).thenReturn(Optional.of(tracked(job, TrackingState.SAVED, 2)));

        assertThatThrownBy(() -> service.allowRecommendation(account, job, 2))
                .isInstanceOf(TrackingExceptions.InvalidTransition.class);
        verify(tracking, never()).delete(any(), any(), anyLong(), any());
    }

    private static TrackedJob tracked(UUID job, TrackingState state, long version) {
        return new TrackedJob(UUID.randomUUID(), job, "Vacante", "Empresa", state, null, version, Instant.now(), List.of());
    }
}
