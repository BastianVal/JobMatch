package mx.jobmatch.cvimport.application;

public final class CvImportExceptions {
    private CvImportExceptions() {}
    public static class InvalidFile extends RuntimeException { public InvalidFile(String message) { super(message); } }
    public static class ImportNotFound extends RuntimeException { public ImportNotFound() { super("Importación no encontrada."); } }
    public static class ImportNotReady extends RuntimeException { public ImportNotReady(String message) { super(message); } }
    public static class CandidateNotFound extends RuntimeException { public CandidateNotFound() { super("Propuesta no encontrada."); } }
    public static class DecisionConflict extends RuntimeException { public DecisionConflict() { super("La propuesta cambió; actualiza la vista."); } }
    public static class DuplicateFile extends RuntimeException { public DuplicateFile() { super("Este CV ya está activo."); } }
    public static class ActiveDocumentLimit extends RuntimeException { public ActiveDocumentLimit() { super("Alcanzaste el máximo de CV activos."); } }
}
