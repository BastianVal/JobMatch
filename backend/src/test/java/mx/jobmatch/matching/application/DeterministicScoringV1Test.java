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
            assertThat(reason.evidence()).containsKeys("profileSkillId","professionalMonths","trajectoryIds");
        }).anySatisfy(reason->{
            assertThat(reason.type()).isEqualTo("GAP");
            assertThat(reason.requirement()).containsEntry("skillId",POSTGRES);
            assertThat(reason.evidence()).isEmpty();
        });
        assertThat(scoring.evaluate(profile,job("MID"),facts).score()).isEqualByComparingTo(result.score());
    }

    @Test void juniorAgainstSeniorIsCappedUnlessProfessionalRequirementIsMet(){
        var profile=profile("JUNIOR",12);
        var noRequirement=new JobFacts(ROLE,"SENIOR",null,List.of(skill(JAVA,"Java","DESIRED")),List.of(),List.of());
        assertThat(scoring.evaluate(profile,job("SENIOR"),noRequirement).score()).isLessThanOrEqualTo(new BigDecimal("44.00"));

        var metRequirement=new JobFacts(ROLE,"SENIOR",12,List.of(skill(JAVA,"Java","DESIRED")),List.of(),List.of());
        assertThat(scoring.evaluate(profile,job("SENIOR"),metRequirement).score()).isGreaterThan(new BigDecimal("44.00"));
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
