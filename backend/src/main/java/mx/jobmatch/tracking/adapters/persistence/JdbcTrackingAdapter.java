package mx.jobmatch.tracking.adapters.persistence;

import mx.jobmatch.tracking.application.TrackingRepository;
import mx.jobmatch.tracking.domain.JobActivity;
import mx.jobmatch.tracking.domain.TrackedJob;
import mx.jobmatch.tracking.domain.TrackingState;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcTrackingAdapter implements TrackingRepository {
    private final JdbcClient jdbc;

    public JdbcTrackingAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public int recordImpressions(UUID accountId, List<UUID> jobIds) {
        int recorded = 0;
        for (UUID jobId : jobIds) {
            recorded += jdbc.sql("""
                    INSERT INTO tracking.job_impression(account_id, canonical_job_id)
                    SELECT account.id, job.id FROM iam.account account, jobs.canonical_job job
                    WHERE account.public_id=:account AND job.public_id=:job
                    ON CONFLICT(account_id, canonical_job_id)
                    DO UPDATE SET last_viewed_at=GREATEST(tracking.job_impression.last_viewed_at, now())
                    """).param("account", accountId).param("job", jobId).update();
        }
        return recorded;
    }

    @Override
    public List<JobActivity> activities(UUID accountId, List<UUID> jobIds) {
        List<JobActivity> activities = new ArrayList<>();
        for (UUID jobId : jobIds) {
            jdbc.sql("""
                    SELECT job.public_id job_id, impression.first_viewed_at, impression.last_viewed_at,
                      tracked.public_id tracking_id, tracked.state, tracked.note, tracked.version,
                      tracked.updated_at tracking_updated_at
                    FROM jobs.canonical_job job
                    JOIN iam.account account ON account.public_id=:account
                    LEFT JOIN tracking.job_impression impression
                      ON impression.account_id=account.id AND impression.canonical_job_id=job.id
                    LEFT JOIN tracking.user_job tracked
                      ON tracked.account_id=account.id AND tracked.canonical_job_id=job.id
                    WHERE job.public_id=:job
                    """).param("account", accountId).param("job", jobId)
                    .query((rs, row) -> new JobActivity(rs.getObject("job_id", UUID.class),
                            rs.getTimestamp("first_viewed_at") == null,
                            instant(rs, "first_viewed_at"), instant(rs, "last_viewed_at"),
                            rs.getObject("tracking_id", UUID.class), state(rs.getString("state")),
                            rs.getString("note"), rs.getObject("version", Long.class),
                            instant(rs, "tracking_updated_at"))).optional().ifPresent(activities::add);
        }
        return List.copyOf(activities);
    }

    @Override
    public Optional<TrackedJob> find(UUID accountId, UUID jobId) {
        return base("WHERE account.public_id=:account AND job.public_id=:value", accountId, jobId).map(this::hydrate);
    }

    @Override
    public Optional<TrackedJob> findById(UUID accountId, UUID trackingId) {
        return base("WHERE account.public_id=:account AND tracked.public_id=:value", accountId, trackingId).map(this::hydrate);
    }

    @Override
    public List<TrackedJob> list(UUID accountId, List<TrackingState> states, int limit) {
        JdbcClient.StatementSpec statement = jdbc.sql("""
                SELECT tracked.id internal_id, tracked.public_id, job.public_id job_id, job.title,
                  employer.canonical_name employer, tracked.state, tracked.note, tracked.version, tracked.updated_at
                FROM tracking.user_job tracked JOIN iam.account account ON account.id=tracked.account_id
                JOIN jobs.canonical_job job ON job.id=tracked.canonical_job_id
                JOIN jobs.employer employer ON employer.id=job.employer_id
                WHERE account.public_id=:account
                  AND tracked.state IN (:states)
                ORDER BY tracked.updated_at DESC, tracked.id DESC LIMIT :limit
                """).param("account", accountId).param("limit", limit)
                .param("states", states.stream().map(TrackingState::name).toList());
        return statement.query(this::row).list().stream().map(this::hydrate).toList();
    }

    @Override
    @Transactional
    public TrackedJob create(UUID accountId, UUID jobId, TrackingState state, String note) {
        UUID id = UUID.randomUUID();
        Optional<Long> internalId = jdbc.sql("""
                INSERT INTO tracking.user_job(public_id, account_id, canonical_job_id, state, note)
                SELECT :id, account.id, job.id, :state, :note
                FROM iam.account account, jobs.canonical_job job
                WHERE account.public_id=:account AND job.public_id=:job
                RETURNING id
                """).param("id", id).param("state", state.name()).param("note", note)
                .param("account", accountId).param("job", jobId).query(Long.class).optional();
        if (internalId.isEmpty()) throw new mx.jobmatch.tracking.application.TrackingExceptions.ResourceNotFound();
        insertEvent(internalId.get(), null, state, note, 1);
        return find(accountId, jobId).orElseThrow();
    }

    @Override
    @Transactional
    public Optional<TrackedJob> update(UUID accountId, UUID jobId, long expectedVersion,
                                       TrackingState fromState, TrackingState targetState, String note) {
        Optional<Long> internalId = jdbc.sql("""
                UPDATE tracking.user_job tracked SET state=:target, note=:note,
                  version=tracked.version+1, updated_at=now()
                FROM iam.account account, jobs.canonical_job job
                WHERE tracked.account_id=account.id AND tracked.canonical_job_id=job.id
                  AND account.public_id=:account AND job.public_id=:job
                  AND tracked.version=:version AND tracked.state=:source
                RETURNING tracked.id
                """).param("target", targetState.name()).param("note", note).param("account", accountId)
                .param("job", jobId).param("version", expectedVersion).param("source", fromState.name())
                .query(Long.class).optional();
        if (internalId.isEmpty()) return Optional.empty();
        insertEvent(internalId.get(), fromState, targetState, note, expectedVersion + 1);
        return find(accountId, jobId);
    }

    @Override
    public void deleteForAccount(UUID accountId) {
        jdbc.sql("""
                DELETE FROM tracking.job_impression impression USING iam.account account
                WHERE impression.account_id=account.id AND account.public_id=:account
                """).param("account", accountId).update();
        jdbc.sql("""
                DELETE FROM tracking.user_job tracked USING iam.account account
                WHERE tracked.account_id=account.id AND account.public_id=:account
                """).param("account", accountId).update();
    }

    private Optional<Row> base(String filter, UUID accountId, UUID value) {
        return jdbc.sql("""
                SELECT tracked.id internal_id, tracked.public_id, job.public_id job_id, job.title,
                  employer.canonical_name employer, tracked.state, tracked.note, tracked.version, tracked.updated_at
                FROM tracking.user_job tracked JOIN iam.account account ON account.id=tracked.account_id
                JOIN jobs.canonical_job job ON job.id=tracked.canonical_job_id
                JOIN jobs.employer employer ON employer.id=job.employer_id
                """ + filter).param("account", accountId).param("value", value).query(this::row).optional();
    }

    private TrackedJob hydrate(Row row) {
        List<TrackedJob.Event> history = jdbc.sql("""
                SELECT public_id, from_state, to_state, note_snapshot, resulting_version, occurred_at
                FROM tracking.user_job_event WHERE user_job_id=:id ORDER BY resulting_version
                """).param("id", row.internalId()).query((rs, index) -> new TrackedJob.Event(
                rs.getObject("public_id", UUID.class), state(rs.getString("from_state")),
                TrackingState.valueOf(rs.getString("to_state")), rs.getString("note_snapshot"),
                rs.getLong("resulting_version"), rs.getTimestamp("occurred_at").toInstant())).list();
        return new TrackedJob(row.id(), row.jobId(), row.title(), row.employer(), row.state(), row.note(),
                row.version(), row.updatedAt(), history);
    }

    private void insertEvent(long trackingId, TrackingState from, TrackingState to, String note, long version) {
        jdbc.sql("""
                INSERT INTO tracking.user_job_event(public_id,user_job_id,from_state,to_state,note_snapshot,resulting_version)
                VALUES (:id,:tracking,:source,:target,:note,:version)
                """).param("id", UUID.randomUUID()).param("tracking", trackingId)
                .param("source", from == null ? null : from.name()).param("target", to.name())
                .param("note", note).param("version", version).update();
    }

    private Row row(ResultSet rs, int index) throws SQLException {
        return new Row(rs.getLong("internal_id"), rs.getObject("public_id", UUID.class),
                rs.getObject("job_id", UUID.class), rs.getString("title"), rs.getString("employer"),
                TrackingState.valueOf(rs.getString("state")), rs.getString("note"), rs.getLong("version"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static TrackingState state(String value) { return value == null ? null : TrackingState.valueOf(value); }
    private static java.time.Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    private record Row(long internalId, UUID id, UUID jobId, String title, String employer,
                       TrackingState state, String note, long version, java.time.Instant updatedAt) {}
}
