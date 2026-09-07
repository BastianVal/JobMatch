package mx.jobmatch.matching.application;

import mx.jobmatch.matching.domain.MatchEvaluation;
import mx.jobmatch.profile.application.ProfileService;
import mx.jobmatch.profile.domain.ProfessionalProfile;
import mx.jobmatch.profile.domain.ProfileDraft;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MatchingServiceTest {
    private final ProfileService profiles=mock(ProfileService.class);
    private final MatchingRepository matches=mock(MatchingRepository.class);
    private final MatchingEvaluator evaluator=mock(MatchingEvaluator.class);
    private final MatchingService service=new MatchingService(profiles,matches,evaluator);

    @Test void returnsUpdatingWithoutCalculatingInsideTheRequest(){
        UUID account=UUID.randomUUID(),profileId=UUID.randomUUID(),role=UUID.randomUUID();
        when(profiles.get(account)).thenReturn(profile(profileId,4,2,role));
        when(matches.recommendationGeneration(profileId)).thenReturn(Optional.of(
                new MatchingRepository.RecommendationGeneration(3,2,Instant.now())));

        var feed=service.recommendations(account,50);

        assertThat(feed.status()).isEqualTo("UPDATING");
        assertThat(feed.profileVersion()).isEqualTo(4);
        assertThat(feed.items()).isEmpty();
        verify(matches,never()).recommendationCandidates(any(),anyInt());
        verifyNoInteractions(evaluator);
    }

    @Test void returnsOnlyTheProjectionForTheCurrentProfileAndCatalog(){
        UUID account=UUID.randomUUID(),profileId=UUID.randomUUID(),role=UUID.randomUUID();
        when(profiles.get(account)).thenReturn(profile(profileId,4,2,role));
        var generatedAt=Instant.now();
        when(matches.recommendationGeneration(profileId)).thenReturn(Optional.of(
                new MatchingRepository.RecommendationGeneration(4,2,generatedAt)));
        var item=new MatchEvaluation.Recommendation(UUID.randomUUID(),"Java Developer","Example",null,
                "REMOTE","FULL_TIME",Instant.now(),java.math.BigDecimal.TEN,"LOW",java.util.Map.of(),List.of());
        when(matches.recommendations(account,4,25)).thenReturn(List.of(item));

        var feed=service.recommendations(account,25);

        assertThat(feed.status()).isEqualTo("READY");
        assertThat(feed.generatedAt()).isEqualTo(generatedAt);
        assertThat(feed.items()).containsExactly(item);
    }

    private static ProfessionalProfile profile(UUID id,long version,int catalogVersion,UUID role){
        var data=new ProfileDraft(null,null,null,null,List.of(new ProfileDraft.TargetRole(UUID.randomUUID(),role,1,"Java")),
                null,List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        return new ProfessionalProfile(id,version,catalogVersion,data);
    }
}
