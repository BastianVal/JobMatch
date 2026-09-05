package mx.jobmatch.ingestion.adapters.connectors;

import org.springframework.stereotype.Component;

@Component
public class LeverConnectorAdapter extends SimulatedConnectorSupport {
    public LeverConnectorAdapter() { super("LEVER", true); }
}
