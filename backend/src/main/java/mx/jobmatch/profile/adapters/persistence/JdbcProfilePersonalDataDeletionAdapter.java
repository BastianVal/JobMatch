package mx.jobmatch.profile.adapters.persistence;

import mx.jobmatch.identity.application.PersonalDataDeletionPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JdbcProfilePersonalDataDeletionAdapter implements PersonalDataDeletionPort {
    private final JdbcClient jdbc;
    public JdbcProfilePersonalDataDeletionAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public void deleteForAccount(UUID accountId) {
        jdbc.sql("""
                DELETE FROM profile.professional_profile
                WHERE account_id=(SELECT id FROM iam.account WHERE public_id=:accountId)
                """).param("accountId", accountId).update();
    }
}
