package mx.jobmatch.matching.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MatchEvaluation(UUID id,UUID jobId,long profileVersion,long jobVersion,int catalogVersion,
                              String scoringVersion,BigDecimal score,String classification,
                              Map<String,BigDecimal> components,List<Reason> reasons,boolean cached) {
    public record Reason(UUID id,String component,String type,UUID requirementId,
                         Map<String,Object> requirement,Map<String,Object> evidence,
                         BigDecimal points,String explanation){}
    public MatchEvaluation cachedCopy(){return new MatchEvaluation(id,jobId,profileVersion,jobVersion,catalogVersion,
            scoringVersion,score,classification,components,reasons,true);}

    public record Recommendation(UUID jobId,String title,String employer,String seniority,String remoteMode,
                                 String employmentType,Instant publishedAt,BigDecimal score,String classification,
                                 Map<String,BigDecimal> components,List<Reason> reasons){}
}
