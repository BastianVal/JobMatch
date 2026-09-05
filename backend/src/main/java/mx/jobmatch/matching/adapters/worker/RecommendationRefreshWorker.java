package mx.jobmatch.matching.adapters.worker;

import mx.jobmatch.matching.application.MatchingRepository;
import mx.jobmatch.matching.application.RecommendationBatchService;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Profile("worker")
@Component
public class RecommendationRefreshWorker {
    private final MatchingRepository matches;private final RecommendationBatchService batches;
    public RecommendationRefreshWorker(MatchingRepository matches,RecommendationBatchService batches){
        this.matches=matches;this.batches=batches;
    }
    @Scheduled(fixedDelay=60_000,initialDelay=15_000)
    public void refresh(){
        for(var accountId:matches.pendingProfileAccounts(10))batches.refresh(accountId);
    }
}
