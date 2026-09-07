package mx.jobmatch.matching.application;

import mx.jobmatch.matching.domain.MatchEvaluation;
import mx.jobmatch.matching.domain.RecommendationFeed;
import mx.jobmatch.profile.application.ProfileService;
import mx.jobmatch.profile.domain.ProfessionalProfile;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static mx.jobmatch.matching.application.MatchingExceptions.*;

@Profile("api")
@Service
public class MatchingService {
    private final ProfileService profiles;private final MatchingRepository matches;
    private final MatchingEvaluator evaluator;
    public MatchingService(ProfileService profiles,MatchingRepository matches,MatchingEvaluator evaluator){
        this.profiles=profiles;this.matches=matches;this.evaluator=evaluator;
    }

    @Transactional
    public MatchEvaluation match(UUID accountId,UUID jobId){
        return evaluator.match(profile(accountId),jobId);
    }

    @Transactional(readOnly=true)
    public MatchEvaluation savedMatch(UUID accountId,UUID resultId){return matches.findById(accountId,resultId).orElseThrow(JobNotFound::new);}

    @Transactional(readOnly=true)
    public RecommendationFeed recommendations(UUID accountId,int limit){
        ProfessionalProfile profile=profile(accountId);
        if(profile.data().targetRoles().isEmpty())return new RecommendationFeed("READY",profile.version(),null,null,List.of());
        var generation=matches.recommendationGeneration(profile.id());
        boolean current=generation.filter(value->value.profileVersion()==profile.version()
                && value.catalogVersion()==profile.catalogVersion()).isPresent();
        var items=current?matches.recommendations(accountId,profile.version(),limit):List.<MatchEvaluation.Recommendation>of();
        return new RecommendationFeed(current?"READY":"UPDATING",profile.version(),
                generation.map(MatchingRepository.RecommendationGeneration::profileVersion).orElse(null),
                generation.map(MatchingRepository.RecommendationGeneration::generatedAt).orElse(null),items);
    }

    private ProfessionalProfile profile(UUID accountId){ProfessionalProfile profile=profiles.get(accountId);if(profile.id()==null)throw new ProfileRequired();return profile;}
}
