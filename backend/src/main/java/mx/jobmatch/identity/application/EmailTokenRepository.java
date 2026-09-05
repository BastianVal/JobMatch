package mx.jobmatch.identity.application;

import mx.jobmatch.identity.domain.EmailTokenPurpose;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailTokenRepository {
    void save(UUID tokenId, UUID accountId, EmailTokenPurpose purpose, String tokenHash, Instant expiresAt);
    Optional<TokenRecord> findUsable(String tokenHash, EmailTokenPurpose purpose, Instant now);
    boolean consume(UUID tokenId, Instant now);
    void deleteForAccount(UUID accountId);

    record TokenRecord(UUID tokenId, UUID accountId) {}
}

