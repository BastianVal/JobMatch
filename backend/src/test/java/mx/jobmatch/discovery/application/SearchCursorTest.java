package mx.jobmatch.discovery.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchCursorTest {
    @Test
    void roundTripsStableSortValues() {
        UUID id = UUID.randomUUID();
        Instant published = Instant.parse("2026-08-12T10:15:30.123456Z");

        SearchCursor decoded = SearchCursor.decode(SearchCursor.encode(new BigDecimal("0.125000"), published, id));

        assertThat(decoded.relevance()).isEqualByComparingTo("0.125000");
        assertThat(decoded.publishedAt()).isEqualTo(published);
        assertThat(decoded.jobId()).isEqualTo(id);
    }

    @Test
    void rejectsMalformedCursor() {
        assertThatThrownBy(() -> SearchCursor.decode("not-a-cursor"))
                .isInstanceOf(DiscoveryExceptions.InvalidSearch.class);
    }
}
