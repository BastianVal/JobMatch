package mx.jobmatch.profile.application;

public final class ProfileExceptions {
    private ProfileExceptions() {}
    public static class VersionConflict extends RuntimeException {}
    public static class InvalidProfile extends RuntimeException {
        public InvalidProfile(String message) { super(message); }
    }
    public static class InvalidCatalogReference extends RuntimeException {}
}
