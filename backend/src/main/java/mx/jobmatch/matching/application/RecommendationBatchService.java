package mx.jobmatch.matching.application;

import mx.jobmatch.discovery.application.JobSearchRepository;
import mx.jobmatch.matching.domain.MatchEvaluation;
import mx.jobmatch.profile.application.ProfileRepository;
import mx.jobmatch.vacancies.domain.JobDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
        matches.replaceRecommendations(profile.id(),profile.version(),balanced(evaluated).stream().map(item->item.match().id()).toList());
    });}
    private static List<EvaluatedJob> balanced(List<EvaluatedJob> ranked) {
        var byRole=new LinkedHashMap<UUID,List<EvaluatedJob>>();
        for(var item:ranked)byRole.computeIfAbsent(item.job().roleFamilyId(),ignored->new ArrayList<>()).add(item);
        var roleOrder=byRole.entrySet().stream().sorted(Comparator
                .comparingInt((java.util.Map.Entry<UUID,List<EvaluatedJob>> entry)->entry.getValue().stream()
                        .mapToInt(EvaluatedJob::targetPriority).min().orElse(11))
                .thenComparing(java.util.Map.Entry::getKey)).map(java.util.Map.Entry::getKey).toList();
        var result=new ArrayList<EvaluatedJob>();var positions=new LinkedHashMap<UUID,Integer>();
        while(result.size()<500&&!byRole.isEmpty()){
            for(var roleId:roleOrder){
                var bucket=byRole.get(roleId);if(bucket==null)continue;
                int index=positions.getOrDefault(roleId,0);
                result.add(bucket.get(index));positions.put(roleId,index+1);
                if(index+1>=bucket.size())byRole.remove(roleId);
                if(result.size()==500)break;
            }
        }
        return result;
    }
    private record EvaluatedJob(JobDetail job, MatchEvaluation match,int targetPriority){}
}
