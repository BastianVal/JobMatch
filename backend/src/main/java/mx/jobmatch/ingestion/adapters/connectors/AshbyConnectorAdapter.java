package mx.jobmatch.ingestion.adapters.connectors;

import org.springframework.stereotype.Component;

@Component
public class AshbyConnectorAdapter extends SimulatedConnectorSupport {
    public AshbyConnectorAdapter() { super("ASHBY", true); }
}
