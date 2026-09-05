package mx.jobmatch.operations.adapters.persistence;

import mx.jobmatch.operations.application.OutboxPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JdbcOutboxAdapter implements OutboxPort {
    private final JdbcClient jdbc;

    public JdbcOutboxAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public UUID append(String aggregateType, UUID aggregatePublicId, String eventType, String payload) {
        UUID eventId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO ops.outbox_event(public_id, aggregate_type, aggregate_public_id, event_type, payload)
                VALUES (:eventId, :aggregateType, :aggregateId, :eventType, CAST(:payload AS jsonb))
                """).param("eventId", eventId).param("aggregateType", aggregateType)
                .param("aggregateId", aggregatePublicId).param("eventType", eventType)
                .param("payload", payload).update();
        return eventId;
    }
}
