package mx.jobmatch.vacancies.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record JobSummary(
        UUID id,
        String title,
        String employer,
        UUID roleFamilyId,
        String roleFamily,
        String seniority,
        String remoteMode,
        String employmentType,
        BigDecimal salaryMinMonthly,
        BigDecimal salaryMaxMonthly,
        String currency,
        String city,
        String state,
        String countryCode,
        Instant publishedAt,
        BigDecimal relevance) {}
