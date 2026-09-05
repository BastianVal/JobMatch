package mx.jobmatch.ingestion.application;

public final class IngestionExceptions {
    private IngestionExceptions() {}
    public static class InvalidRefresh extends RuntimeException {
        public InvalidRefresh(String message) { super(message); }
    }
    public static class RefreshNotFound extends RuntimeException {}
}
