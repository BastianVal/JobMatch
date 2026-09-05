package mx.jobmatch.discovery.application;

public final class DiscoveryExceptions {
    private DiscoveryExceptions() {}

    public static class InvalidSearch extends RuntimeException {
        public InvalidSearch(String message) { super(message); }
    }
    public static class ResourceNotFound extends RuntimeException {}
    public static class VersionConflict extends RuntimeException {}
    public static class SavedSearchLimitReached extends RuntimeException {}
}
