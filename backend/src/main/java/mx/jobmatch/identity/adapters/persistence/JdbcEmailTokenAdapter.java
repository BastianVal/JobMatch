package mx.jobmatch.identity.adapters.persistence;

import mx.jobmatch.identity.application.EmailTokenRepository;
import mx.jobmatch.identity.domain.EmailTokenPurpose;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcEmailTokenAdapter implements EmailTokenRepository {
    private final JdbcClient jdbc;

    public JdbcEmailTokenAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public void save(UUID tokenId, UUID accountId, EmailTokenPurpose purpose, String tokenHash, Instant expiresAt) {
        jdbc.sql("""
                INSERT INTO iam.email_token(public_id, account_id, purpose, token_hash, expires_at)
                SELECT :tokenId, id, :purpose, :tokenHash, :expiresAt
                FROM iam.account WHERE public_id=:accountId
                """).param("tokenId", tokenId).param("accountId", accountId).param("purpose", purpose.name())
                .param("tokenHash", tokenHash).param("expiresAt", Timestamp.from(expiresAt)).update();
    }

    @Override
    public Optional<TokenRecord> findUsable(String tokenHash, EmailTokenPurpose purpose, Instant now) {
        return jdbc.sql("""
                SELECT token.public_id token_id, account.public_id account_id
                FROM iam.email_token token JOIN iam.account account ON account.id=token.account_id
                WHERE token.token_hash=:tokenHash AND token.purpose=:purpose
                  AND token.consumed_at IS NULL AND token.expires_at > :now
                """).param("tokenHash", tokenHash).param("purpose", purpose.name())
                .param("now", Timestamp.from(now)).query((rs, row) -> new TokenRecord(
                        rs.getObject("token_id", UUID.class), rs.getObject("account_id", UUID.class))).optional();
    }

    @Override
    public boolean consume(UUID tokenId, Instant now) {
        return jdbc.sql("""
                UPDATE iam.email_token SET consumed_at=:now
                WHERE public_id=:tokenId AND consumed_at IS NULL AND expires_at > :now
                """).param("tokenId", tokenId).param("now", Timestamp.from(now)).update() == 1;
    }

    @Override
    public void deleteForAccount(UUID accountId) {
        jdbc.sql("""
                DELETE FROM iam.email_token WHERE account_id=(SELECT id FROM iam.account WHERE public_id=:accountId)
                """).param("accountId", accountId).update();
    }
}
