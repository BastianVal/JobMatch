package mx.jobmatch.cvimport.adapters.persistence;

import mx.jobmatch.cvimport.application.CvStoragePort;
import mx.jobmatch.identity.application.PersonalDataDeletionPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JdbcCvPersonalDataDeletionAdapter implements PersonalDataDeletionPort {
    private final JdbcClient jdbc;private final CvStoragePort storage;
    public JdbcCvPersonalDataDeletionAdapter(JdbcClient jdbc,CvStoragePort storage){this.jdbc=jdbc;this.storage=storage;}
    @Override public void deleteForAccount(UUID accountId){
        jdbc.sql("SELECT file.storage_key FROM cvimport.stored_file file JOIN iam.account account ON account.id=file.account_id WHERE account.public_id=:id")
                .param("id",accountId).query(String.class).list().forEach(storage::delete);
        jdbc.sql("DELETE FROM cvimport.stored_file file USING iam.account account WHERE file.account_id=account.id AND account.public_id=:id")
                .param("id",accountId).update();
    }
}
