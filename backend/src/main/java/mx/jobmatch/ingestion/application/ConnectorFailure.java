package mx.jobmatch.ingestion.application;

public class ConnectorFailure extends RuntimeException {
    private final String safeCode;
    private final boolean retryable;

    public ConnectorFailure(String safeCode, boolean retryable) {
        super(safeCode);
        this.safeCode = safeCode;
        this.retryable = retryable;
    }
    public String safeCode() { return safeCode; }
    public boolean retryable() { return retryable; }
}
