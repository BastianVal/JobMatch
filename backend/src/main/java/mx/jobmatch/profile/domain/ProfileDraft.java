package mx.jobmatch.profile.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProfileDraft(
        String headline,
        String summary,
        String location,
        String seniority,
        List<TargetRole> targetRoles,
        Preference preferences,
        List<String> excludedEmployers,
        List<TrajectoryItem> trajectory,
        List<Education> education,
        List<Certification> certifications,
        List<Language> languages,
        List<Skill> skills) {

    public ProfileDraft {
        targetRoles = safe(targetRoles);
        excludedEmployers = safe(excludedEmployers);
        trajectory = safe(trajectory);
        education = safe(education);
        certifications = safe(certifications);
        languages = safe(languages);
        skills = safe(skills);
    }

    private static <T> List<T> safe(List<T> values) { return values == null ? List.of() : List.copyOf(values); }

    public record TargetRole(UUID id, UUID roleFamilyId, int priority, String name) {}
    public record Preference(String remoteMode, String employmentType, BigDecimal minimumMonthlySalary,
                             String currency, boolean willingToRelocate) {}
    public record TrajectoryItem(UUID id, TrajectoryType type, String title, String organization,
                                 String description, int startYear, int startMonth,
                                 Integer endYear, Integer endMonth, boolean current) {}
    public record Education(UUID id, String institution, String degree, String fieldOfStudy,
                            Integer startYear, Integer endYear) {}
    public record Certification(UUID id, String name, String issuer, Integer issuedYear,
                                String credentialId, String credentialUrl) {}
    public record Language(UUID id, String code, String name, String proficiency) {}
    public record Skill(UUID id, UUID catalogSkillId, String customName, String name, String proficiency,
                        boolean matchEligible, List<UUID> evidenceTrajectoryIds,
                        int professionalMonths, BigDecimal weightedPracticalMonths) {
        public Skill {
            evidenceTrajectoryIds = evidenceTrajectoryIds == null ? List.of() : List.copyOf(evidenceTrajectoryIds);
        }
    }
}
