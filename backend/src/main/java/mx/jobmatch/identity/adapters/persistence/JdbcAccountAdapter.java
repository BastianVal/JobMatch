package mx.jobmatch.identity.adapters.persistence;

import mx.jobmatch.identity.application.AccountRepository;
import mx.jobmatch.identity.domain.Account;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAccountAdapter implements AccountRepository {
    private final JdbcClient jdbc;

    public JdbcAccountAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Account create(UUID publicId, String email, String passwordHash) {
        return jdbc.sql("""
                INSERT INTO iam.account(public_id, email, password_hash, status)
                VALUES (:publicId, :email, :passwordHash, 'PENDING_VERIFICATION')
                RETURNING public_id, email, password_hash, status, version
                """).param("publicId", publicId).param("email", email).param("passwordHash", passwordHash)
                .query(this::map).single();
    }

    @Override
    public Optional<Account> findByEmail(String normalizedEmail) {
        return jdbc.sql("""
                SELECT public_id, email, password_hash, status, version
                FROM iam.account WHERE email = :email
                """).param("email", normalizedEmail).query(this::map).optional();
    }

    @Override
    public Optional<Account> findByPublicId(UUID publicId) {
        return jdbc.sql("""
                SELECT public_id, email, password_hash, status, version
                FROM iam.account WHERE public_id = :publicId
                """).param("publicId", publicId).query(this::map).optional();
    }

    @Override
    public boolean activate(UUID publicId) {
        return jdbc.sql("""
                UPDATE iam.account SET status='ACTIVE', verified_at=now(), updated_at=now(), version=version+1
                WHERE public_id=:publicId AND status='PENDING_VERIFICATION'
                """).param("publicId", publicId).update() == 1;
    }

    @Override
    public boolean updatePassword(UUID publicId, String passwordHash) {
        return jdbc.sql("""
                UPDATE iam.account SET password_hash=:passwordHash, updated_at=now(), version=version+1
                WHERE public_id=:publicId AND status='ACTIVE'
                """).param("publicId", publicId).param("passwordHash", passwordHash).update() == 1;
    }

    @Override
    public boolean anonymizeAndMarkDeletionPending(UUID publicId) {
        return jdbc.sql("""
                UPDATE iam.account
                SET email='deleted+' || public_id || '@invalid.local',
                    password_hash='{deleted}' || public_id,
                    status='DELETION_PENDING', updated_at=now(), version=version+1
                WHERE public_id=:publicId AND status <> 'DELETION_PENDING'
                """).param("publicId", publicId).update() == 1;
    }

    private Account map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Account(rs.getObject("public_id", UUID.class), rs.getString("email"),
                rs.getString("password_hash"), Account.Status.valueOf(rs.getString("status")),
                rs.getLong("version"));
    }
}
