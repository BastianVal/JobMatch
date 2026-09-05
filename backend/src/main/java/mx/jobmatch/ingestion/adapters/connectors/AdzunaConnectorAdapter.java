package mx.jobmatch.ingestion.adapters.connectors;

import org.springframework.stereotype.Component;

@Component
public class AdzunaConnectorAdapter extends SimulatedConnectorSupport {
    public AdzunaConnectorAdapter() { super("ADZUNA", true); }
}
