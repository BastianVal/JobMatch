package mx.jobmatch.ingestion.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DeduplicationPolicyTest {
    @Test
    void automaticallyMergesStrongIndependentMatch() {
        var posting = normalized("Desarrollador Backend Java", "Tecnología Ejemplo",
                "Construcción de APIs con Java y PostgreSQL.");
        var candidate = new DeduplicationPolicy.Candidate(1, posting.titleNormalized(),
                posting.employerNormalized(), posting.description(), posting.cityNormalized(),
                posting.salaryMaxMonthly(), posting.publishedAt());

        assertThat(DeduplicationPolicy.evaluate(posting, candidate).decision())
                .isEqualTo(DeduplicationPolicy.Decision.AUTO_MERGED);
    }

    @Test
    void keepsUnrelatedVacanciesDistinct() {
        var posting = normalized("Desarrollador Backend Java", "Tecnología Ejemplo", "Java y PostgreSQL");
        var candidate = new DeduplicationPolicy.Candidate(2, "contador fiscal", "otra empresa",
                "impuestos y contabilidad", "monterrey", new BigDecimal("20000"),
                Instant.parse("2026-07-01T00:00:00Z"));

        assertThat(DeduplicationPolicy.evaluate(posting, candidate).decision())
                .isEqualTo(DeduplicationPolicy.Decision.DISTINCT);
    }

    private static NormalizedPosting normalized(String title, String employer, String description) {
        return PostingNormalizer.normalize("JOOBLE", new RawPosting("1", "https://example.test/1", title,
                employer, description, "MX", "Ciudad de México", "Ciudad de México", "HYBRID", "FULL_TIME",
                "MID", new BigDecimal("45000"), new BigDecimal("65000"), "MXN",
                Instant.parse("2026-09-01T00:00:00Z"), "{}"));
    }
}
