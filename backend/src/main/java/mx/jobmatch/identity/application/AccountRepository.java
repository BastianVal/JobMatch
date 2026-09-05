package mx.jobmatch.identity.application;

import mx.jobmatch.identity.domain.Account;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {
    Account create(UUID publicId, String email, String passwordHash);
    Optional<Account> findByEmail(String normalizedEmail);
    Optional<Account> findByPublicId(UUID publicId);
    boolean activate(UUID publicId);
    boolean updatePassword(UUID publicId, String passwordHash);
    boolean anonymizeAndMarkDeletionPending(UUID publicId);
}

