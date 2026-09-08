package mx.jobmatch.matching.application;

import mx.jobmatch.matching.domain.JobFacts;
import mx.jobmatch.profile.domain.ProfessionalProfile;
import mx.jobmatch.profile.domain.ProfileDraft;
import mx.jobmatch.profile.domain.TrajectoryType;
import mx.jobmatch.vacancies.domain.JobDetail;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicScoringV1Test {
    private static final UUID ROLE=UUID.fromString("11000000-0000-0000-0000-000000000001");
    private static final UUID JAVA=UUID.fromString("12000000-0000-0000-0000-000000000001");
    private static final UUID POSTGRES=UUID.fromString("12000000-0000-0000-0000-000000000003");
    private final DeterministicScoringV1 scoring=new DeterministicScoringV1();

    @Test void mandatoryTechnologyGapCapsResultAndPersistsConcreteEvidence(){
        var profile=profile("SENIOR",36);
        var facts=new JobFacts(ROLE,"MID",24,List.of(skill(JAVA,"Java","REQUIRED"),skill(POSTGRES,"PostgreSQL","REQUIRED")),List.of(),List.of("APIs"));

        var result=scoring.evaluate(profile,job("MID"),facts);

        assertThat(result.score()).isLessThanOrEqualTo(new BigDecimal("64.00"));
        assertThat(result.reasons()).anySatisfy(reason->{
            assertThat(reason.type()).isEqualTo("MATCH");
            assertThat(reason.requirement()).containsEntry("skillId",JAVA);
            assertThat(reason.evidence()).containsKeys("skill","professionalMonths","trajectoryIds");
        }).anySatisfy(reason->{
            assertThat(reason.type()).isEqualTo("GAP");
            assertThat(reason.requirement()).containsEntry("skillId",POSTGRES);
            assertThat(reason.evidence()).isEmpty();
        });
        assertThat(scoring.evaluate(profile,job("MID"),facts).score()).isEqualByComparingTo(result.score());
    }

    @Test void combinesMissingSkillPriorityConsiderationsIntoOneClearReason(){
        var result=scoring.evaluate(profile("SENIOR",36),job("MID"),
                new JobFacts(ROLE,"MID",null,List.of(skill(JAVA,"Java","REQUIRED")),List.of(),List.of()));

        assertThat(result.reasons().stream().filter(reason->reason.component().equals("TECHNOLOGIES_KNOWLEDGE"))
                .filter(reason->reason.type().equals("CONSIDERATION")).filter(reason->reason.explanation().contains("no especifica")))
                .singleElement().satisfies(reason->{
                    assertThat(reason.explanation()).contains("deseables", "relacionadas con responsabilidades");
                    assertThat(reason.requirement()).containsKey("unspecifiedPriorities");
                });
    }

    @Test void projectEvidenceNamesTheTrajectoryAndRelevantSkills(){
        UUID project=UUID.randomUUID();
        var data=new ProfileDraft("Backend",null,"CDMX","MID",
                List.of(new ProfileDraft.TargetRole(UUID.randomUUID(),ROLE,1,"Software")),null,List.of(),
                List.of(new ProfileDraft.TrajectoryItem(project,TrajectoryType.PERSONAL_PROJECT,"API de pagos","Proyecto personal",null,2024,1,2024,3,false)),
                List.of(),List.of(),List.of(),List.of(new ProfileDraft.Skill(UUID.randomUUID(),JAVA,null,"Java","ADVANCED",true,
                List.of(project),0,new BigDecimal("2.5"))));
        var result=scoring.evaluate(new ProfessionalProfile(UUID.randomUUID(),1,1,data),job("MID"),
                new JobFacts(ROLE,"MID",null,List.of(skill(JAVA,"Java","REQUIRED")),List.of(),List.of()));

        assertThat(result.reasons()).anySatisfy(reason->{
            assertThat(reason.component()).isEqualTo("RELEVANT_PROJECTS");
            assertThat(reason.evidence()).containsEntry("projects",List.of(Map.of(
                    "name","API de pagos — Proyecto personal","skills",List.of("Java"))));
        });
    }

    @Test void juniorAgainstSeniorIsCappedUnlessProfessionalRequirementIsMet(){
        var profile=profile("JUNIOR",12);
        var noRequirement=new JobFacts(ROLE,"SENIOR",null,List.of(skill(JAVA,"Java","DESIRED")),List.of(),List.of());
        assertThat(scoring.evaluate(profile,job("SENIOR"),noRequirement).score()).isLessThanOrEqualTo(new BigDecimal("44.00"));

        var metRequirement=new JobFacts(ROLE,"SENIOR",12,List.of(skill(JAVA,"Java","DESIRED")),List.of(),List.of());
        assertThat(scoring.evaluate(profile,job("SENIOR"),metRequirement).score()).isGreaterThan(new BigDecimal("44.00"));
    }

    @Test void missingJobSeniorityIsNeutralAndNeverBreaksCompatibilityCalculation(){
        var result=scoring.evaluate(profile("JUNIOR",12),job(null),
                new JobFacts(null,null,null,List.of(),List.of(),List.of()));

        assertThat(result.score()).isPositive();
        assertThat(result.reasons()).anySatisfy(reason->{
            assertThat(reason.component()).isEqualTo("SENIORITY_EXPERIENCE");
            assertThat(reason.type()).isEqualTo("CONSIDERATION");
        });
    }

    private static ProfessionalProfile profile(String seniority,int months){
        UUID trajectory=UUID.randomUUID(),profileSkill=UUID.randomUUID();
        int endMonth=months;int endYear=2020+(endMonth-1)/12;int month=(endMonth-1)%12+1;
        var data=new ProfileDraft("Backend",null,"CDMX",seniority,
                List.of(new ProfileDraft.TargetRole(UUID.randomUUID(),ROLE,1,"Software")),null,List.of(),
                List.of(new ProfileDraft.TrajectoryItem(trajectory,TrajectoryType.EMPLOYMENT,"Backend","Example",null,2020,1,endYear,month,false)),
                List.of(),List.of(),List.of(),List.of(new ProfileDraft.Skill(profileSkill,JAVA,null,"Java","ADVANCED",true,
                List.of(trajectory),months,new BigDecimal(months))));
        return new ProfessionalProfile(UUID.randomUUID(),3,1,data);
    }
    private static JobDetail job(String seniority){return new JobDetail(UUID.randomUUID(),2,"Backend Engineer","Employer",ROLE,"Software",
            "Java APIs",seniority,"HYBRID","FULL_TIME",new BigDecimal("50000"),new BigDecimal("70000"),"MXN",Instant.now(),"ACTIVE",
            List.of(),List.of(),List.of(),List.of());}
    private static JobFacts.SkillRequirement skill(UUID id,String name,String priority){return new JobFacts.SkillRequirement(UUID.randomUUID(),id,name,priority,name);}
}
