package mx.jobmatch.matching.application;

public final class MatchingExceptions {
    private MatchingExceptions(){}
    public static class ProfileRequired extends RuntimeException {}
    public static class JobNotFound extends RuntimeException {}
    public static class InvalidMatchingRequest extends RuntimeException {
        public InvalidMatchingRequest(String message){super(message);}
    }
}
