package mx.jobmatch.matching.application;

import mx.jobmatch.matching.domain.JobFacts;
import mx.jobmatch.matching.domain.MatchEvaluation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchingRepository {
    List<CatalogSkill> catalogSkills();
    Optional<JobFacts> findFacts(UUID jobId,long jobVersion,int catalogVersion,String extractorVersion);
    void saveFacts(UUID jobId,long jobVersion,int catalogVersion,String extractorVersion,JobFacts facts);
    Optional<MatchEvaluation> find(UUID profileId,UUID jobId,long profileVersion,long jobVersion,int catalogVersion,String scoringVersion);
    Optional<MatchEvaluation> findById(UUID accountId,UUID resultId);
    MatchEvaluation save(UUID profileId,MatchEvaluation evaluation);
    List<UUID> recommendationCandidates(UUID accountId,int maximum);
    List<UUID> pendingProfileAccounts(int maximum);
    void replaceRecommendations(UUID profileId,long profileVersion,List<UUID> matchResultIds);
    int deleteObsolete();
    record CatalogSkill(UUID id,String name,List<String> aliases){}
}
