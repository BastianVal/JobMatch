package mx.jobmatch.matching.application;

import mx.jobmatch.discovery.application.JobSearchRepository;
import mx.jobmatch.matching.domain.MatchEvaluation;
import mx.jobmatch.profile.application.ProfileService;
import mx.jobmatch.profile.domain.ProfessionalProfile;
import mx.jobmatch.vacancies.domain.JobDetail;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static mx.jobmatch.matching.application.MatchingExceptions.*;

@Profile("api")
@Service
public class MatchingService {
    private static final int MAXIMUM_CANDIDATES=2_000;
    private final ProfileService profiles;private final JobSearchRepository jobs;private final MatchingRepository matches;
    private final MatchingEvaluator evaluator;
    public MatchingService(ProfileService profiles,JobSearchRepository jobs,MatchingRepository matches,MatchingEvaluator evaluator){
        this.profiles=profiles;this.jobs=jobs;this.matches=matches;this.evaluator=evaluator;
    }

    @Transactional
    public MatchEvaluation match(UUID accountId,UUID jobId){
        return evaluator.match(profile(accountId),jobId);
    }

    @Transactional(readOnly=true)
    public MatchEvaluation savedMatch(UUID accountId,UUID resultId){return matches.findById(accountId,resultId).orElseThrow(JobNotFound::new);}

    @Transactional
    public List<MatchEvaluation.Recommendation> recommendations(UUID accountId,int limit){
        ProfessionalProfile profile=profile(accountId);
        if(profile.data().targetRoles().isEmpty())return List.of();
        var evaluated=new ArrayList<EvaluatedJob>();
        for(UUID id:matches.recommendationCandidates(accountId,MAXIMUM_CANDIDATES)){
            JobDetail job=jobs.find(id).orElse(null);if(job!=null)evaluated.add(new EvaluatedJob(job,evaluator.evaluate(profile,job)));
        }
        evaluated.sort(Comparator.comparing((EvaluatedJob item)->item.match().score()).reversed()
                .thenComparing(item->item.job().publishedAt(),Comparator.reverseOrder()).thenComparing(item->item.job().id()));
        matches.replaceRecommendations(profile.id(),profile.version(),evaluated.stream().limit(500).map(item->item.match().id()).toList());
        return evaluated.stream().limit(limit).map(item->new MatchEvaluation.Recommendation(item.job().id(),item.job().title(),
                item.job().employer(),item.job().seniority(),item.job().remoteMode(),item.job().employmentType(),item.job().publishedAt(),
                item.match().score(),item.match().classification(),item.match().components(),item.match().reasons())).toList();
    }

    private ProfessionalProfile profile(UUID accountId){ProfessionalProfile profile=profiles.get(accountId);if(profile.id()==null)throw new ProfileRequired();return profile;}
    private record EvaluatedJob(JobDetail job,MatchEvaluation match){}
}
