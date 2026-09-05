package mx.jobmatch.identity.application;

import java.time.Instant;

public interface AuthAttemptPort {
    long countEmailFailuresSince(String emailHash, Instant since);
    long countIpFailuresSince(String ipHash, Instant since);
    void record(String emailHash, String ipHash, Result result);
    enum Result { SUCCESS, FAILURE, BLOCKED }
}

