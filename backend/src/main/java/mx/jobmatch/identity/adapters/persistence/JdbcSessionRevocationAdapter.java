package mx.jobmatch.identity.adapters.persistence;

import mx.jobmatch.identity.application.SessionRevocationPort;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.util.UUID;

@Profile("api")
@Component
public class JdbcSessionRevocationAdapter implements SessionRevocationPort {
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public JdbcSessionRevocationAdapter(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    @Override
    public void revokeAll(UUID accountId) {
        sessions.findByPrincipalName(accountId.toString()).keySet().forEach(sessions::deleteById);
    }
}
