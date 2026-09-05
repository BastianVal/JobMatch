package mx.jobmatch.tracking.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingStateTest {
    @Test
    void supportsTheApplicationLifecycleAndTerminalStates() {
        assertThat(TrackingState.canStartAt(TrackingState.SAVED)).isTrue();
        assertThat(TrackingState.canStartAt(TrackingState.APPLIED)).isTrue();
        assertThat(TrackingState.SAVED.canTransitionTo(TrackingState.APPLIED)).isTrue();
        assertThat(TrackingState.APPLIED.canTransitionTo(TrackingState.INTERVIEW)).isTrue();
        assertThat(TrackingState.INTERVIEW.canTransitionTo(TrackingState.OFFER)).isTrue();
        assertThat(TrackingState.OFFER.canTransitionTo(TrackingState.ACCEPTED)).isTrue();
        assertThat(TrackingState.ACCEPTED.allowedTargets()).isEmpty();
    }

    @Test
    void rejectsSkippedOrReopenedTransitions() {
        assertThat(TrackingState.SAVED.canTransitionTo(TrackingState.OFFER)).isFalse();
        assertThat(TrackingState.REJECTED.canTransitionTo(TrackingState.SAVED)).isFalse();
        assertThat(TrackingState.WITHDRAWN.canTransitionTo(TrackingState.APPLIED)).isFalse();
    }
}
