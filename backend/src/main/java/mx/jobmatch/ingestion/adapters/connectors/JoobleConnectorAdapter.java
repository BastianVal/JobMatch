package mx.jobmatch.ingestion.adapters.connectors;

import org.springframework.stereotype.Component;

@Component
public class JoobleConnectorAdapter extends SimulatedConnectorSupport {
    public JoobleConnectorAdapter() { super("JOOBLE", false); }
}
