package mx.jobmatch.ingestion.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record NormalizedPosting(
        String sourceKey, String externalId, String url, String urlHash,
        String title, String titleNormalized, String employer, String employerNormalized,
        String description, String countryCode, String state, String stateNormalized,
        String city, String cityNormalized, String remoteMode, String employmentType,
        String seniority, BigDecimal salaryMinMonthly, BigDecimal salaryMaxMonthly,
        String currency, Instant publishedAt, String identityKey, String payload, String payloadHash) {}
