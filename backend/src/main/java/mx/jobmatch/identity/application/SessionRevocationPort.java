package mx.jobmatch.identity.application;

import java.util.UUID;

public interface SessionRevocationPort {
    void revokeAll(UUID accountId);
}

