package mx.jobmatch.cvimport.domain;

import java.time.Instant;
import java.util.UUID;

public record CvDocumentSummary(UUID id, String originalFilename, Instant createdAt,
                                String latestImportStatus) {}
