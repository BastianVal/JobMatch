package mx.jobmatch.vacancies.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobDetail(
        UUID id,
        long version,
        String title,
        String employer,
        UUID roleFamilyId,
        String roleFamily,
        String description,
        String seniority,
        String remoteMode,
        String employmentType,
        BigDecimal salaryMinMonthly,
        BigDecimal salaryMaxMonthly,
        String currency,
        Instant publishedAt,
        String status,
        List<Location> locations,
        List<SourceLink> sourceLinks,
        List<Requirement> requirements,
        List<SkillRequirement> skillRequirements) {

    public record Location(UUID id, String countryCode, String state, String city) {}
    public record SourceLink(UUID postingId, String source, String url, String type, boolean preferred) {}
    public record Requirement(UUID id, String text, String category, boolean mandatory) {}
    public record SkillRequirement(UUID id, UUID skillId, String skill, String priority, String evidenceText) {}
}
