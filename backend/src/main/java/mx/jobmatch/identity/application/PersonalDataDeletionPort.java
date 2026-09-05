package mx.jobmatch.identity.application;

import java.util.UUID;

public interface PersonalDataDeletionPort {
    void deleteForAccount(UUID accountId);
}
