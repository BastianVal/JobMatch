package mx.jobmatch.ingestion.adapters.connectors;

import mx.jobmatch.ingestion.application.ConnectorFailure;
import mx.jobmatch.ingestion.application.ConnectorPort;
import mx.jobmatch.ingestion.domain.ConnectorQuery;
import mx.jobmatch.ingestion.domain.PostingNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorContractTest {
    @Test
    void everyConnectorProducesTheCanonicalRawContract() {
        List<ConnectorPort> connectors = List.of(new JoobleConnectorAdapter(), new AdzunaConnectorAdapter(),
                new GreenhouseConnectorAdapter(), new LeverConnectorAdapter(), new AshbyConnectorAdapter());
        var query = new ConnectorQuery(UUID.randomUUID(), "Backend", "México", null);

        assertThat(connectors).allSatisfy(connector -> {
            var page = connector.fetch(query);
            assertThat(page.postings()).hasSize(2).allSatisfy(posting -> {
                assertThat(posting.externalId()).isNotBlank();
                assertThat(posting.url()).startsWith("https://");
                assertThat(posting.title()).isNotBlank();
                assertThat(PostingNormalizer.normalize(connector.sourceKey(), posting).identityKey()).hasSize(64);
            });
        });
    }

    @Test
    void simulatedFailureIsIsolatedToRequestedSource() {
        var query = new ConnectorQuery(UUID.randomUUID(), "FAIL_JOOBLE", null, null);
        assertThatThrownBy(() -> new JoobleConnectorAdapter().fetch(query)).isInstanceOf(ConnectorFailure.class);
        assertThat(new AdzunaConnectorAdapter().fetch(query).postings()).hasSize(2);
    }
}
