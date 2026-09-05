package mx.jobmatch.ingestion.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record RawPosting(
        String externalId, String url, String title, String employer, String description,
        String countryCode, String state, String city, String remoteMode, String employmentType,
        String seniority, BigDecimal salaryMinMonthly, BigDecimal salaryMaxMonthly, String currency,
        Instant publishedAt, String payload) {}
