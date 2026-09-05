package mx.jobmatch.tracking.adapters.persistence;

import mx.jobmatch.identity.application.PersonalDataDeletionPort;
import mx.jobmatch.tracking.application.TrackingRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JdbcTrackingPersonalDataDeletionAdapter implements PersonalDataDeletionPort {
    private final TrackingRepository tracking;
    public JdbcTrackingPersonalDataDeletionAdapter(TrackingRepository tracking) { this.tracking = tracking; }
    @Override public void deleteForAccount(UUID accountId) { tracking.deleteForAccount(accountId); }
}
