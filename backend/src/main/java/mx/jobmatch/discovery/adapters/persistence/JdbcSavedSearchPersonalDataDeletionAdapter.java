package mx.jobmatch.discovery.adapters.persistence;

import mx.jobmatch.identity.application.PersonalDataDeletionPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JdbcSavedSearchPersonalDataDeletionAdapter implements PersonalDataDeletionPort {
    private final JdbcClient jdbc;

    public JdbcSavedSearchPersonalDataDeletionAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public void deleteForAccount(UUID accountId) {
        jdbc.sql("""
                DELETE FROM profile.saved_search search USING iam.account account
                WHERE search.account_id=account.id AND account.public_id=:accountId
                """).param("accountId", accountId).update();
    }
}
