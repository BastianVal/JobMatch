package mx.jobmatch.operations.adapters.persistence;

import mx.jobmatch.operations.application.IdempotencyPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcIdempotencyAdapter implements IdempotencyPort {
    private final JdbcClient jdbc;
    public JdbcIdempotencyAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Record> find(String operation, String key) {
        return jdbc.sql("""
                SELECT request_hash, resource_public_id FROM ops.idempotency_record
                WHERE operation=:operation AND idempotency_key=:key AND expires_at > now()
                """).param("operation", operation).param("key", key)
                .query((rs, row) -> new Record(rs.getString(1), rs.getObject(2, UUID.class))).optional();
    }

    @Override
    public void save(String operation, String key, String hash, UUID resourceId, Instant expiresAt) {
        jdbc.sql("""
                INSERT INTO ops.idempotency_record(operation, idempotency_key, request_hash, resource_public_id, expires_at)
                VALUES (:operation, :key, :hash, :resourceId, :expiresAt)
                """).param("operation", operation).param("key", key).param("hash", hash)
                .param("resourceId", resourceId).param("expiresAt", Timestamp.from(expiresAt)).update();
    }
}
