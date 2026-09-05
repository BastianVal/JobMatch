package mx.jobmatch.identity.application;

public final class IdentityExceptions {
    private IdentityExceptions() {}
    public static class InvalidToken extends RuntimeException {}
    public static class InvalidCredentials extends RuntimeException {}
    public static class AuthenticationRateLimited extends RuntimeException {}
    public static class AccountNotFound extends RuntimeException {}
    public static class InvalidPassword extends RuntimeException {}
}

