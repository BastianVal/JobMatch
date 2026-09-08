package mx.jobmatch.ingestion.adapters.connectors;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                new LeverConnectorAdapter(), new AshbyConnectorAdapter());
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
    void greenhouseNormalizesPublicBoardPayloadAndNamespacesExternalIds() throws Exception {
        String payload = """
                {"jobs":[{"id":123,"absolute_url":"https://boards.greenhouse.io/c3iot/jobs/123",
                "title":"Backend Engineer","content":"&amp;lt;h2&amp;gt;Responsabilidades&amp;lt;/h2&amp;gt;&amp;lt;p&amp;gt;Java &amp;amp; APIs&amp;lt;/p&amp;gt;&amp;lt;ul&amp;gt;&amp;lt;li&amp;gt;Spring Boot&amp;lt;/li&amp;gt;&amp;lt;/ul&amp;gt;",
                "updated_at":"2026-09-01T12:00:00Z","location":{"name":"Guadalajara, Jalisco, Mexico"}}]}""";
        var query = new ConnectorQuery(UUID.randomUUID(), null, null, null, "c3iot", "C3 AI", "MX");
        var page = GreenhouseConnectorAdapter.parse(payload, query, new ObjectMapper());

        assertThat(page.completeResponse()).isTrue();
        assertThat(page.postings()).singleElement().satisfies(posting -> {
            assertThat(posting.externalId()).isEqualTo("greenhouse:c3iot:123");
            assertThat(posting.employer()).isEqualTo("C3 AI");
            assertThat(posting.description()).isEqualTo("Responsabilidades\nJava & APIs\n\n• Spring Boot");
            assertThat(posting.countryCode()).isEqualTo("MX");
            assertThat(posting.city()).isEqualTo("Guadalajara");
            assertThat(PostingNormalizer.normalize("GREENHOUSE", posting).identityKey()).hasSize(64);
        });
    }

    @Test
    void simulatedFailureIsIsolatedToRequestedSource() {
        var query = new ConnectorQuery(UUID.randomUUID(), "FAIL_JOOBLE", null, null);
        assertThatThrownBy(() -> new JoobleConnectorAdapter().fetch(query)).isInstanceOf(ConnectorFailure.class);
        assertThat(new AdzunaConnectorAdapter().fetch(query).postings()).hasSize(2);
    }
}
