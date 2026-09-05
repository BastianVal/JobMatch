package mx.jobmatch.matching.adapters.web;

import mx.jobmatch.identity.adapters.security.AccountPrincipal;
import mx.jobmatch.matching.application.MatchingService;
import mx.jobmatch.matching.domain.MatchEvaluation;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Profile("api")
@RestController
@RequestMapping("/api/v1")
public class MatchingController {
    private final MatchingService matching;
    public MatchingController(MatchingService matching){this.matching=matching;}
    @GetMapping("/jobs/{jobId}/match")
    MatchEvaluation match(@AuthenticationPrincipal AccountPrincipal principal,@PathVariable UUID jobId){return matching.match(principal.accountId(),jobId);}
    @GetMapping("/matches/{resultId}")
    MatchEvaluation savedMatch(@AuthenticationPrincipal AccountPrincipal principal,@PathVariable UUID resultId){return matching.savedMatch(principal.accountId(),resultId);}
    @GetMapping("/me/recommendations")
    List<MatchEvaluation.Recommendation> recommendations(@AuthenticationPrincipal AccountPrincipal principal,
                                                          @RequestParam(defaultValue="50")int limit){
        if(limit<1||limit>100)throw new mx.jobmatch.matching.application.MatchingExceptions.InvalidMatchingRequest("El límite debe estar entre 1 y 100.");
        return matching.recommendations(principal.accountId(),limit);
    }
}
