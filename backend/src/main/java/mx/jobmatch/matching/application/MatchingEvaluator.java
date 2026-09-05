package mx.jobmatch.matching.application;

import mx.jobmatch.discovery.application.JobSearchRepository;
import mx.jobmatch.matching.domain.JobFacts;
import mx.jobmatch.matching.domain.MatchEvaluation;
import mx.jobmatch.profile.domain.ProfessionalProfile;
import mx.jobmatch.vacancies.domain.JobDetail;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MatchingEvaluator {
    private final MatchingRepository matches;private final JobSearchRepository jobs;
    private final JobFactExtractor extractor;private final DeterministicScoringV1 scoring;
    public MatchingEvaluator(MatchingRepository matches,JobSearchRepository jobs,JobFactExtractor extractor,DeterministicScoringV1 scoring){
        this.matches=matches;this.jobs=jobs;this.extractor=extractor;this.scoring=scoring;
    }
    public MatchEvaluation match(ProfessionalProfile profile,UUID jobId){return evaluate(profile,jobs.find(jobId)
            .orElseThrow(MatchingExceptions.JobNotFound::new));}
    public MatchEvaluation evaluate(ProfessionalProfile profile,JobDetail job){
        var cached=matches.find(profile.id(),job.id(),profile.version(),job.version(),profile.catalogVersion(),DeterministicScoringV1.VERSION);
        if(cached.isPresent())return cached.get();
        JobFacts facts=matches.findFacts(job.id(),job.version(),profile.catalogVersion(),JobFactExtractor.VERSION).orElseGet(()->{
            JobFacts extracted=extractor.extract(job,matches.catalogSkills());
            matches.saveFacts(job.id(),job.version(),profile.catalogVersion(),JobFactExtractor.VERSION,extracted);return extracted;
        });
        return matches.save(profile.id(),scoring.evaluate(profile,job,facts));
    }
}
