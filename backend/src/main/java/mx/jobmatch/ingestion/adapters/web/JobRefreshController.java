package mx.jobmatch.ingestion.adapters.web;

import mx.jobmatch.identity.adapters.security.AccountPrincipal;
import mx.jobmatch.ingestion.application.IngestionService;
import mx.jobmatch.ingestion.application.IngestionExceptions.InvalidRefresh;
import mx.jobmatch.ingestion.domain.JobRefresh;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@Profile("api")
@RestController
@RequestMapping("/api/v1/job-refreshes")
public class JobRefreshController {
    private final IngestionService ingestion;
    public JobRefreshController(IngestionService ingestion) { this.ingestion = ingestion; }

    @PostMapping
    ResponseEntity<JobRefresh> schedule(@AuthenticationPrincipal AccountPrincipal principal,
                                        @RequestBody RefreshRequest request) {
        if (request == null) throw new InvalidRefresh("La solicitud es obligatoria.");
        JobRefresh refresh = ingestion.schedule(principal.accountId(), request.roleFamilyId(),
                request.query(), request.location());
        return ResponseEntity.accepted().location(URI.create("/api/v1/job-refreshes/" + refresh.id())).body(refresh);
    }

    @GetMapping("/{id}")
    JobRefresh get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id) {
        return ingestion.find(principal.accountId(), id);
    }

    public record RefreshRequest(UUID roleFamilyId, String query, String location) {}
}
