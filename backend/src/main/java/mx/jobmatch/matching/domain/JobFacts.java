package mx.jobmatch.matching.domain;

import java.util.List;
import java.util.UUID;

public record JobFacts(UUID roleFamilyId, String seniority, Integer professionalMonths,
                       List<SkillRequirement> skills, List<LanguageRequirement> languages,
                       List<String> responsibilities) {
    public JobFacts {
        skills=skills==null?List.of():List.copyOf(skills);
        languages=languages==null?List.of():List.copyOf(languages);
        responsibilities=responsibilities==null?List.of():List.copyOf(responsibilities);
    }
    public record SkillRequirement(UUID requirementId,UUID skillId,String skill,String priority,String evidenceText){}
    public record LanguageRequirement(UUID requirementId,String code,String name,boolean mandatory,String evidenceText){}
}
