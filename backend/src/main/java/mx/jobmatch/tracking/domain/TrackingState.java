package mx.jobmatch.tracking.domain;

import java.util.EnumSet;
import java.util.Set;

public enum TrackingState {
    SAVED, DISCARDED, APPLIED, INTERVIEW, OFFER, ACCEPTED, REJECTED, WITHDRAWN;

    public static boolean canStartAt(TrackingState target) {
        return target == SAVED || target == DISCARDED || target == APPLIED;
    }

    public boolean canTransitionTo(TrackingState target) {
        if (this == target) return true;
        return switch (this) {
            case SAVED -> EnumSet.of(DISCARDED, APPLIED).contains(target);
            case DISCARDED -> target == SAVED;
            case APPLIED -> EnumSet.of(INTERVIEW, REJECTED, WITHDRAWN).contains(target);
            case INTERVIEW -> EnumSet.of(OFFER, REJECTED, WITHDRAWN).contains(target);
            case OFFER -> EnumSet.of(ACCEPTED, REJECTED, WITHDRAWN).contains(target);
            case ACCEPTED, REJECTED, WITHDRAWN -> false;
        };
    }

    public Set<TrackingState> allowedTargets() {
        return switch (this) {
            case SAVED -> EnumSet.of(DISCARDED, APPLIED);
            case DISCARDED -> EnumSet.of(SAVED);
            case APPLIED -> EnumSet.of(INTERVIEW, REJECTED, WITHDRAWN);
            case INTERVIEW -> EnumSet.of(OFFER, REJECTED, WITHDRAWN);
            case OFFER -> EnumSet.of(ACCEPTED, REJECTED, WITHDRAWN);
            case ACCEPTED, REJECTED, WITHDRAWN -> EnumSet.noneOf(TrackingState.class);
        };
    }
}
