package mx.jobmatch.matching.adapters.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.jobmatch.matching.application.RecommendationBatchService;
import mx.jobmatch.operations.application.BackgroundTaskHandler;
import mx.jobmatch.operations.application.NonRetryableTaskException;
import mx.jobmatch.operations.domain.BackgroundTask;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Profile("worker")
@Component
public class RecommendationRefreshTaskHandler implements BackgroundTaskHandler {
    private final RecommendationBatchService batches;
    private final ObjectMapper json;

    public RecommendationRefreshTaskHandler(RecommendationBatchService batches,ObjectMapper json){
        this.batches=batches;this.json=json;
    }

    @Override public boolean supports(String type){return "REFRESH_RECOMMENDATIONS".equals(type);}

    @Override public void handle(BackgroundTask task){batches.refresh(accountId(task.payload()));}

    private UUID accountId(String payload){
        try{return json.readValue(payload,Payload.class).accountId();}
        catch(Exception failure){throw new NonRetryableTaskException("INVALID_RECOMMENDATION_TASK");}
    }

    private record Payload(UUID accountId){}
}
