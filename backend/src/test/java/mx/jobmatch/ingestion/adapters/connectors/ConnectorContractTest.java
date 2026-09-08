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
        List<ConnectorPort> connectors = List.of(new JoobleConnectorAdapter(), new AdzunaConnectorAdapter());
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
    void greenhouseExcludesExplicitForeignLocationsWithRemoteQualifiers() throws Exception {
        String payload = """
                {"jobs":[
                {"id":1,"absolute_url":"https://boards.greenhouse.io/cookunity/jobs/1","title":"Foreign role",
                "content":"Description","updated_at":"2026-09-01T12:00:00Z","location":{"name":"Argentina (Remote)"}},
                {"id":2,"absolute_url":"https://boards.greenhouse.io/cookunity/jobs/2","title":"Mexican role",
                "content":"Description","updated_at":"2026-09-01T12:00:00Z","location":{"name":"Ciudad de México (Remote), Mexico"}}
                ]}""";
        var query = new ConnectorQuery(UUID.randomUUID(), null, null, null, "cookunity", "CookUnity", "MX");

        var page = GreenhouseConnectorAdapter.parse(payload, query, new ObjectMapper());

        assertThat(page.postings()).singleElement().satisfies(posting -> {
            assertThat(posting.externalId()).isEqualTo("greenhouse:cookunity:2");
            assertThat(posting.countryCode()).isEqualTo("MX");
        });
    }

    @Test
    void leverNormalizesMexicanPostingsAndSkipsForeignLocations() throws Exception {
        String payload = """
                [{"id":"mx-1","text":"Backend Engineer","hostedUrl":"https://jobs.lever.co/acme/mx-1",
                "descriptionPlain":"Java APIs","createdAt":1760000000000,
                "categories":{"location":"Ciudad de México, Mexico","commitment":"Full-time"}},
                {"id":"ar-1","text":"Foreign Engineer","hostedUrl":"https://jobs.lever.co/acme/ar-1",
                "categories":{"location":"Buenos Aires, Argentina"}}]""";
        var query = new ConnectorQuery(UUID.randomUUID(), null, null, null, "acme", "Acme", "MX");
        var page = LeverConnectorAdapter.parse(payload, query, new ObjectMapper());

        assertThat(page.postings()).singleElement().satisfies(posting -> {
            assertThat(posting.externalId()).isEqualTo("lever:acme:mx-1");
            assertThat(posting.countryCode()).isEqualTo("MX");
            assertThat(posting.employmentType()).isEqualTo("FULL_TIME");
        });
    }

    @Test
    void ashbyNormalizesMexicanPostingsAndSkipsForeignLocations() throws Exception {
        String payload = """
                {"jobs":[{"id":"mx-1","title":"Platform Engineer","jobUrl":"https://jobs.ashbyhq.com/acme/mx-1",
                "descriptionHtml":"<p>Docker</p>","isListed":true,"isRemote":true,"employmentType":"FullTime",
                "publishedAt":"2026-09-01T12:00:00Z","location":"Ciudad de México",
                "address":{"postalAddress":{"addressCountry":"Mexico"}}},
                {"id":"br-1","title":"Foreign Engineer","jobUrl":"https://jobs.ashbyhq.com/acme/br-1",
                "isListed":true,"location":"Sao Paulo","address":{"postalAddress":{"addressCountry":"Brazil"}}}]}""";
        var query = new ConnectorQuery(UUID.randomUUID(), null, null, null, "acme", "Acme", "MX");
        var page = AshbyConnectorAdapter.parse(payload, query, new ObjectMapper());

        assertThat(page.postings()).singleElement().satisfies(posting -> {
            assertThat(posting.externalId()).isEqualTo("ashby:acme:mx-1");
            assertThat(posting.countryCode()).isEqualTo("MX");
            assertThat(posting.remoteMode()).isEqualTo("REMOTE");
        });
    }

    @Test
    void simulatedFailureIsIsolatedToRequestedSource() {
        var query = new ConnectorQuery(UUID.randomUUID(), "FAIL_JOOBLE", null, null);
        assertThatThrownBy(() -> new JoobleConnectorAdapter().fetch(query)).isInstanceOf(ConnectorFailure.class);
        assertThat(new AdzunaConnectorAdapter().fetch(query).postings()).hasSize(2);
    }
}
