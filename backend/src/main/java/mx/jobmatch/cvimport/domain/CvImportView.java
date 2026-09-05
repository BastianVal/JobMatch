package mx.jobmatch.cvimport.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CvImportView(UUID id, UUID documentId, String originalFilename, String status,
                           long profileBaseVersion, String extractorVersion, String safeErrorCode,
                           Instant expiresAt, Long confirmedProfileVersion, List<Candidate> candidates) {
    public record Candidate(UUID id, String type, int payloadVersion, Map<String, Object> proposal,
                            String decision, long decisionVersion, Duplicate duplicate) {}
    public record Duplicate(UUID id, String existingEntityType, UUID existingEntityId,
                            double similarityScore, String resolution) {}
}
