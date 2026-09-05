package mx.jobmatch.identity.adapters.persistence;

import mx.jobmatch.identity.application.AuthAttemptPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class JdbcAuthAttemptAdapter implements AuthAttemptPort {
    private final JdbcClient jdbc;

    public JdbcAuthAttemptAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public long countEmailFailuresSince(String emailHash, Instant since) {
        return count("email_hash", emailHash, since);
    }

    @Override
    public long countIpFailuresSince(String ipHash, Instant since) {
        return count("ip_hash", ipHash, since);
    }

    private long count(String column, String hash, Instant since) {
        return jdbc.sql("SELECT count(*) FROM iam.auth_attempt WHERE " + column
                        + "=:hash AND result <> 'SUCCESS' AND attempted_at >= :since")
                .param("hash", hash).param("since", Timestamp.from(since)).query(Long.class).single();
    }

    @Override
    public void record(String emailHash, String ipHash, Result result) {
        jdbc.sql("INSERT INTO iam.auth_attempt(email_hash, ip_hash, result) VALUES (:email, :ip, :result)")
                .param("email", emailHash).param("ip", ipHash).param("result", result.name()).update();
    }
}
