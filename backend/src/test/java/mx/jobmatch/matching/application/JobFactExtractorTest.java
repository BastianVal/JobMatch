package mx.jobmatch.matching.application;

import mx.jobmatch.vacancies.domain.JobDetail;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobFactExtractorTest {
    @Test void extractsVersionedDeterministicFactsFromJobText(){
        UUID java=UUID.randomUUID(),role=UUID.randomUUID(),jobId=UUID.randomUUID();
        var job=new JobDetail(jobId,7,"Senior Backend Engineer","Example",role,"Software",
                "Java es indispensable. Inglés requerido. Se requieren 3 años de experiencia profesional.","SENIOR","REMOTE","FULL_TIME",
                null,null,null,Instant.now(),"ACTIVE",List.of(),List.of(),List.of(),List.of());
        var catalog=List.of(new MatchingRepository.CatalogSkill(java,"Java",List.of("java")));
        var extractor=new JobFactExtractor();

        var first=extractor.extract(job,catalog);var second=extractor.extract(job,catalog);

        assertThat(first.professionalMonths()).isEqualTo(36);
        assertThat(first.skills()).singleElement().satisfies(skill->{assertThat(skill.skillId()).isEqualTo(java);assertThat(skill.priority()).isEqualTo("REQUIRED");});
        assertThat(first.languages()).singleElement().satisfies(language->{assertThat(language.code()).isEqualTo("en");assertThat(language.mandatory()).isTrue();});
        assertThat(second.skills().getFirst().requirementId()).isEqualTo(first.skills().getFirst().requirementId());
    }
}
