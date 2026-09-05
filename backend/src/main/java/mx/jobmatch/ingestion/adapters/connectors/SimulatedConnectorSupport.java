package mx.jobmatch.ingestion.adapters.connectors;

import mx.jobmatch.ingestion.application.ConnectorFailure;
import mx.jobmatch.ingestion.application.ConnectorPort;
import mx.jobmatch.ingestion.domain.ConnectorPage;
import mx.jobmatch.ingestion.domain.ConnectorQuery;
import mx.jobmatch.ingestion.domain.RawPosting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

abstract class SimulatedConnectorSupport implements ConnectorPort {
    private final String sourceKey;
    private final boolean completeResponse;

    SimulatedConnectorSupport(String sourceKey, boolean completeResponse) {
        this.sourceKey = sourceKey;
        this.completeResponse = completeResponse;
    }

    @Override public String sourceKey() { return sourceKey; }

    @Override
    public ConnectorPage fetch(ConnectorQuery query) {
        if (query.roleQuery() != null && query.roleQuery().toUpperCase(java.util.Locale.ROOT)
                .startsWith("FAIL_" + sourceKey))
            throw new ConnectorFailure("SIMULATED_" + sourceKey + "_FAILURE", true);
        Instant published = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(1, ChronoUnit.DAYS);
        RawPosting shared = new RawPosting("shared-backend-java", "https://" + sourceKey.toLowerCase() +
                ".example.test/jobs/shared-backend-java?utm_source=simulator",
                "Desarrollador Backend Java", "Tecnología Ejemplo", "Construcción de APIs con Java y PostgreSQL.",
                "MX", "Ciudad de México", "Ciudad de México", "HYBRID", "FULL_TIME", "MID",
                new BigDecimal("45000"), new BigDecimal("65000"), "MXN", published,
                "{\"source\":\"" + sourceKey + "\",\"simulated\":true}");
        RawPosting unique = new RawPosting(sourceKey.toLowerCase() + "-platform-engineer",
                "https://" + sourceKey.toLowerCase() + ".example.test/jobs/platform-engineer",
                sourceKey + " Platform Engineer", "Empresa " + sourceKey,
                "Automatización de plataforma, Docker y operación confiable.", "MX", "Jalisco", "Guadalajara",
                "REMOTE", "CONTRACT", "SENIOR", new BigDecimal("60000"), new BigDecimal("85000"), "MXN",
                published.minus(1, ChronoUnit.HOURS), "{\"source\":\"" + sourceKey + "\",\"simulated\":true}");
        return new ConnectorPage(List.of(shared, unique), null, completeResponse);
    }
}
