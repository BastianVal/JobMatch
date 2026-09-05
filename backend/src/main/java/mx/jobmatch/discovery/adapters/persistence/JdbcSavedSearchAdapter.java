package mx.jobmatch.discovery.adapters.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.jobmatch.discovery.application.DiscoveryExceptions.InvalidSearch;
import mx.jobmatch.discovery.application.DiscoveryExceptions.ResourceNotFound;
import mx.jobmatch.discovery.application.SavedSearchRepository;
import mx.jobmatch.discovery.domain.JobSearchCriteria;
import mx.jobmatch.discovery.domain.SavedSearch;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcSavedSearchAdapter implements SavedSearchRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public JdbcSavedSearchAdapter(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<SavedSearch> findAll(UUID accountId) {
        return jdbc.sql("""
                SELECT search.public_id, search.name, search.criteria::text, search.version,
                       search.created_at, search.updated_at
                FROM profile.saved_search search JOIN iam.account account ON account.id=search.account_id
                WHERE account.public_id=:accountId AND account.status='ACTIVE'
                ORDER BY search.updated_at DESC, search.id DESC
                """).param("accountId", accountId).query(this::map).list();
    }

    @Override
    public int count(UUID accountId) {
        return jdbc.sql("""
                WITH owner AS (
                  SELECT id FROM iam.account WHERE public_id=:accountId AND status='ACTIVE' FOR UPDATE
                )
                SELECT count(*) FROM profile.saved_search search JOIN owner ON owner.id=search.account_id
                """).param("accountId", accountId).query(Integer.class).single();
    }

    @Override
    public SavedSearch create(UUID accountId, String name, JobSearchCriteria criteria) {
        try {
            return jdbc.sql("""
                    INSERT INTO profile.saved_search(public_id, account_id, name, criteria)
                    SELECT :id, id, :name, CAST(:criteria AS jsonb) FROM iam.account
                    WHERE public_id=:accountId AND status='ACTIVE'
                    RETURNING public_id, name, criteria::text, version, created_at, updated_at
                    """).param("id", UUID.randomUUID()).param("name", name).param("criteria", write(criteria))
                    .param("accountId", accountId).query(this::map).optional().orElseThrow(ResourceNotFound::new);
        } catch (DataIntegrityViolationException duplicate) {
            throw new InvalidSearch("Ya existe una búsqueda guardada con ese nombre.");
        }
    }

    @Override
    public Optional<SavedSearch> update(UUID accountId, UUID id, long expectedVersion, String name,
                                        JobSearchCriteria criteria) {
        try {
            return jdbc.sql("""
                    UPDATE profile.saved_search search SET name=:name, criteria=CAST(:criteria AS jsonb),
                        version=search.version+1, updated_at=now()
                    FROM iam.account account
                    WHERE search.account_id=account.id AND account.public_id=:accountId
                      AND account.status='ACTIVE' AND search.public_id=:id AND search.version=:version
                    RETURNING search.public_id, search.name, search.criteria::text, search.version,
                              search.created_at, search.updated_at
                    """).param("name", name).param("criteria", write(criteria)).param("accountId", accountId)
                    .param("id", id).param("version", expectedVersion).query(this::map).optional();
        } catch (DataIntegrityViolationException duplicate) {
            throw new InvalidSearch("Ya existe una búsqueda guardada con ese nombre.");
        }
    }

    @Override
    public boolean delete(UUID accountId, UUID id, long expectedVersion) {
        return jdbc.sql("""
                DELETE FROM profile.saved_search search USING iam.account account
                WHERE search.account_id=account.id AND account.public_id=:accountId
                  AND account.status='ACTIVE' AND search.public_id=:id AND search.version=:version
                """).param("accountId", accountId).param("id", id).param("version", expectedVersion).update() == 1;
    }

    @Override
    public boolean exists(UUID accountId, UUID id) {
        return jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM profile.saved_search search
                  JOIN iam.account account ON account.id=search.account_id
                  WHERE account.public_id=:accountId AND account.status='ACTIVE' AND search.public_id=:id)
                """).param("accountId", accountId).param("id", id).query(Boolean.class).single();
    }

    private SavedSearch map(ResultSet rs, int row) throws SQLException {
        try {
            return new SavedSearch(rs.getObject("public_id", UUID.class), rs.getString("name"),
                    json.readValue(rs.getString("criteria"), JobSearchCriteria.class), rs.getLong("version"),
                    rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
        } catch (JsonProcessingException invalid) {
            throw new SQLException("Invalid saved-search criteria", invalid);
        }
    }

    private String write(JobSearchCriteria criteria) {
        try { return json.writeValueAsString(criteria); }
        catch (JsonProcessingException impossible) { throw new IllegalStateException(impossible); }
    }
}
