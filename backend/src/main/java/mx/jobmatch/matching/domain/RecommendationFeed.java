package mx.jobmatch.matching.domain;

import java.time.Instant;
import java.util.List;

public record RecommendationFeed(String status, long profileVersion, Long generatedProfileVersion,
                                 Instant generatedAt, List<MatchEvaluation.Recommendation> items) {
    public RecommendationFeed {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
