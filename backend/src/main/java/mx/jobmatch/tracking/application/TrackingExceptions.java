package mx.jobmatch.tracking.application;

public final class TrackingExceptions {
    private TrackingExceptions() {}
    public static final class ResourceNotFound extends RuntimeException {}
    public static final class VersionConflict extends RuntimeException {}
    public static final class InvalidTransition extends RuntimeException {
        public InvalidTransition(String message) { super(message); }
    }
    public static final class InvalidTrackingRequest extends RuntimeException {
        public InvalidTrackingRequest(String message) { super(message); }
    }
}
