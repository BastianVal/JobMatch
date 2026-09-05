package mx.jobmatch.ingestion.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PostingNormalizerTest {
    @Test
    void removesTrackingAndBuildsSourceIndependentIdentity() {
        RawPosting first = posting("https://Jobs.Example/x?utm_source=a&z=2", "Tecnología Éjemplo");
        RawPosting second = posting("https://other.example/x", "Tecnologia Ejemplo");

        var a = PostingNormalizer.normalize("JOOBLE", first);
        var b = PostingNormalizer.normalize("ADZUNA", second);

        assertThat(a.url()).isEqualTo("https://jobs.example/x?z=2");
        assertThat(a.employerNormalized()).isEqualTo("tecnologia ejemplo");
        assertThat(a.identityKey()).isEqualTo(b.identityKey());
    }

    private static RawPosting posting(String url, String employer) {
        return new RawPosting("external", url, "Desarrollador Backend Java", employer,
                "Java Java y PostgreSQL", "mx", "Ciudad de México", "Ciudad de México", "hybrid",
                "full_time", "mid", new BigDecimal("40000"), new BigDecimal("60000"), "mxn",
                Instant.parse("2026-09-01T10:00:00Z"), "{}");
    }
}
