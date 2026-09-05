package mx.jobmatch.ingestion.adapters.persistence;

import mx.jobmatch.identity.application.PersonalDataDeletionPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JdbcIngestionPersonalDataDeletionAdapter implements PersonalDataDeletionPort {
    private final JdbcClient jdbc;
    public JdbcIngestionPersonalDataDeletionAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public void deleteForAccount(UUID accountId) {
        jdbc.sql("""
                DELETE FROM ingestion.query_demand demand USING iam.account account
                WHERE demand.account_id=account.id AND account.public_id=:accountId
                """).param("accountId", accountId).update();
        jdbc.sql("""
                DELETE FROM ingestion.job_refresh refresh USING iam.account account
                WHERE refresh.account_id=account.id AND account.public_id=:accountId
                """).param("accountId", accountId).update();
    }
}
