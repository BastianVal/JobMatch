package mx.jobmatch.discovery.adapters.web;

import mx.jobmatch.discovery.application.DiscoveryExceptions.InvalidSearch;
import mx.jobmatch.discovery.application.DiscoveryService;
import mx.jobmatch.discovery.domain.JobSearchCriteria;
import mx.jobmatch.discovery.domain.SavedSearch;
import mx.jobmatch.identity.adapters.security.AccountPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Profile("api")
@RestController
@RequestMapping("/api/v1/me/saved-searches")
public class SavedSearchController {
    private final DiscoveryService discovery;

    public SavedSearchController(DiscoveryService discovery) { this.discovery = discovery; }

    @GetMapping
    List<SavedSearch> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return discovery.savedSearches(principal.accountId());
    }

    @PostMapping
    ResponseEntity<SavedSearch> create(@AuthenticationPrincipal AccountPrincipal principal,
                                       @RequestBody SavedSearchRequest request) {
        if (request == null) throw new InvalidSearch("La solicitud es obligatoria.");
        SavedSearch saved = discovery.create(principal.accountId(), request.name(), request.criteria());
        return ResponseEntity.created(URI.create("/api/v1/me/saved-searches/" + saved.id()))
                .eTag(Long.toString(saved.version())).body(saved);
    }

    @PutMapping("/{id}")
    ResponseEntity<SavedSearch> update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id,
                                       @RequestHeader("If-Match") String ifMatch,
                                       @RequestBody SavedSearchRequest request) {
        if (request == null) throw new InvalidSearch("La solicitud es obligatoria.");
        SavedSearch saved = discovery.update(principal.accountId(), id, version(ifMatch),
                request.name(), request.criteria());
        return ResponseEntity.ok().eTag(Long.toString(saved.version())).body(saved);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id,
                                @RequestHeader("If-Match") String ifMatch) {
        discovery.delete(principal.accountId(), id, version(ifMatch));
        return ResponseEntity.noContent().build();
    }

    private static long version(String header) {
        try {
            String value = header.strip();
            if (value.startsWith("W/")) value = value.substring(2);
            return Long.parseLong(value.replace("\"", ""));
        } catch (RuntimeException invalid) {
            throw new InvalidSearch("If-Match no es válido.");
        }
    }

    public record SavedSearchRequest(String name, JobSearchCriteria criteria) {}
}
