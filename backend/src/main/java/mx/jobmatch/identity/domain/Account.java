package mx.jobmatch.identity.domain;

import java.util.UUID;

public record Account(UUID publicId, String email, String passwordHash, Status status, long version) {
    public enum Status { PENDING_VERIFICATION, ACTIVE, DELETION_PENDING }
    public boolean canAuthenticate() { return status == Status.ACTIVE; }
}

