package mx.jobmatch.operations.adapters.persistence;

import mx.jobmatch.operations.application.BackgroundTaskPort;
import mx.jobmatch.operations.domain.BackgroundTask;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcBackgroundTaskAdapter implements BackgroundTaskPort {
    private final JdbcClient jdbc;

    public JdbcBackgroundTaskAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public BackgroundTask enqueue(String type, String payload, String key, Instant availableAt) {
        return enqueue(type, payload, key, availableAt, 5);
    }

    @Override
    public BackgroundTask enqueue(String type, String payload, String key, Instant availableAt, int maxAttempts) {
        UUID id = UUID.randomUUID();
        return jdbc.sql("""
                INSERT INTO ops.background_task(public_id, task_type, payload, deduplication_key, available_at, max_attempts)
                VALUES (:id, :type, CAST(:payload AS jsonb), :key, :availableAt, :maxAttempts)
                ON CONFLICT (task_type, deduplication_key) DO UPDATE SET task_type = EXCLUDED.task_type
                RETURNING public_id, task_type, payload::text, deduplication_key, status, attempts, available_at
                """).param("id", id).param("type", type).param("payload", payload).param("key", key)
                .param("availableAt", Timestamp.from(availableAt)).param("maxAttempts", maxAttempts)
                .query(this::map).single();
    }

    @Override
    public Optional<BackgroundTask> find(UUID publicId) {
        return jdbc.sql("""
                SELECT public_id, task_type, payload::text, deduplication_key, status, attempts, available_at
                FROM ops.background_task WHERE public_id = :id
                """).param("id", publicId).query(this::map).optional();
    }

    @Override
    public Optional<BackgroundTask> claimNext(String workerId, Duration lease) {
        return jdbc.sql("""
                WITH candidate AS (
                    SELECT id FROM ops.background_task
                    WHERE (status = 'PENDING' AND available_at <= now())
                       OR (status = 'RUNNING' AND leased_until < now())
                    ORDER BY priority DESC, available_at, id
                    FOR UPDATE SKIP LOCKED LIMIT 1
                )
                UPDATE ops.background_task task
                SET status = 'RUNNING', attempts = attempts + 1,
                    leased_by = :workerId, leased_until = now() + (:leaseSeconds * interval '1 second'), updated_at = now()
                FROM candidate WHERE task.id = candidate.id
                RETURNING task.public_id, task.task_type, task.payload::text, task.deduplication_key,
                          task.status, task.attempts, task.available_at
                """).param("workerId", workerId).param("leaseSeconds", lease.toSeconds()).query(this::map).optional();
    }

    @Override
    public void complete(UUID publicId, String workerId) {
        jdbc.sql("""
                UPDATE ops.background_task SET status='COMPLETED', leased_until=NULL, leased_by=NULL, updated_at=now()
                WHERE public_id=:id AND status='RUNNING' AND leased_by=:workerId
                """).param("id", publicId).param("workerId", workerId).update();
    }

    @Override
    public void retry(UUID publicId, String workerId, String error, Instant availableAt) {
        jdbc.sql("""
                UPDATE ops.background_task
                SET status=CASE WHEN attempts >= max_attempts THEN 'FAILED' ELSE 'PENDING' END,
                    available_at=:availableAt, leased_until=NULL, leased_by=NULL, last_error_code=:error, updated_at=now()
                WHERE public_id=:id AND status='RUNNING' AND leased_by=:workerId
                """).param("id", publicId).param("workerId", workerId).param("error", error)
                .param("availableAt", Timestamp.from(availableAt)).update();
    }

    @Override
    public void fail(UUID publicId, String workerId, String error) {
        jdbc.sql("""
                UPDATE ops.background_task
                SET status='FAILED', leased_until=NULL, leased_by=NULL, last_error_code=:error, updated_at=now()
                WHERE public_id=:id AND status='RUNNING' AND leased_by=:workerId
                """).param("id", publicId).param("workerId", workerId).param("error", error).update();
    }

    private BackgroundTask map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new BackgroundTask(rs.getObject("public_id", UUID.class), rs.getString("task_type"),
                rs.getString("payload"), rs.getString("deduplication_key"),
                BackgroundTask.Status.valueOf(rs.getString("status")), rs.getInt("attempts"),
                rs.getTimestamp("available_at").toInstant());
    }
}
