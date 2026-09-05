package mx.jobmatch.ingestion.adapters.connectors;

import org.springframework.stereotype.Component;

@Component
public class GreenhouseConnectorAdapter extends SimulatedConnectorSupport {
    public GreenhouseConnectorAdapter() { super("GREENHOUSE", true); }
}
