package mx.jobmatch.cvimport.application;

import mx.jobmatch.cvimport.domain.CvImportView;
import mx.jobmatch.cvimport.domain.CvDocumentSummary;
import mx.jobmatch.cvimport.domain.ImportProposal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CvImportRepository {
    CvImportView create(UUID accountId, String originalFilename, CvStoragePort.StoredFile file,
                        long profileBaseVersion, Instant expiresAt, String extractorVersion);
    Optional<CvImportView> find(UUID accountId, UUID importId);
    List<CvDocumentSummary> listDocuments(UUID accountId);
    Optional<DocumentFile> documentFile(UUID accountId, UUID documentId);
    Optional<ExtractionInput> startExtraction(UUID importId);
    void completeExtraction(UUID importId, String textHash, List<ImportProposal> proposals);
    void failExtraction(UUID importId, String safeErrorCode);
    CvImportView decide(UUID accountId, UUID importId, UUID candidateId, long expectedVersion,
                        String decision, String duplicateResolution);
    Confirmation lockForConfirmation(UUID accountId, UUID importId);
    void markConfirmed(UUID importId, long profileVersion);
    List<String> storageKeysForAccount(UUID accountId);
    Optional<String> deleteDocument(UUID accountId, UUID documentId);
    void expireReady(Instant now);

    record ExtractionInput(UUID importId, UUID accountId, String storageKey, String mediaType) {}
    record DocumentFile(String storageKey, String originalFilename, String mediaType) {}
    record Confirmation(CvImportView view) {}
}
