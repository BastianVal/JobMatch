package mx.jobmatch.operations.application;

public class NonRetryableTaskException extends RuntimeException {
    private final String safeCode;

    public NonRetryableTaskException(String safeCode) {
        super(safeCode);
        this.safeCode = safeCode;
    }

    public String safeCode() { return safeCode; }
}
