package mx.jobmatch.matching.application;

import mx.jobmatch.discovery.application.JobSearchRepository;
import mx.jobmatch.matching.domain.MatchEvaluation;
import mx.jobmatch.profile.application.ProfileRepository;
import mx.jobmatch.vacancies.domain.JobDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;

@Service
public class RecommendationBatchService {
    private final MatchingRepository matches;private final ProfileRepository profiles;
    private final JobSearchRepository jobs;private final MatchingEvaluator evaluator;
    public RecommendationBatchService(MatchingRepository matches,ProfileRepository profiles,JobSearchRepository jobs,MatchingEvaluator evaluator){
        this.matches=matches;this.profiles=profiles;this.jobs=jobs;this.evaluator=evaluator;
    }
    @Transactional
    public void refresh(UUID accountId){profiles.findByAccount(accountId).ifPresent(profile->{
        var evaluated=new ArrayList<EvaluatedJob>();
        for(var jobId:matches.recommendationCandidates(accountId,2_000))jobs.find(jobId).ifPresent(job->
                evaluated.add(new EvaluatedJob(job,evaluator.evaluate(profile,job),profile.data().targetRoles().stream()
                        .filter(role->role.roleFamilyId().equals(job.roleFamilyId())).mapToInt(role->role.priority())
                        .findFirst().orElse(11))));
        evaluated.sort(Comparator.comparing((EvaluatedJob item)->item.match().score()).reversed()
                .thenComparingInt(EvaluatedJob::targetPriority)
                .thenComparing(item->item.job().publishedAt(),Comparator.reverseOrder()).thenComparing(item->item.job().id()));
        matches.replaceRecommendations(profile.id(),profile.version(),evaluated.stream().limit(500).map(item->item.match().id()).toList());
    });}
    private record EvaluatedJob(JobDetail job, MatchEvaluation match,int targetPriority){}
}
