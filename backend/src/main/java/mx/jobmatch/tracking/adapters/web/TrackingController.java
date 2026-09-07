package mx.jobmatch.tracking.adapters.web;

import mx.jobmatch.identity.adapters.security.AccountPrincipal;
import mx.jobmatch.tracking.application.TrackingService;
import mx.jobmatch.tracking.domain.JobActivity;
import mx.jobmatch.tracking.domain.TrackedJob;
import mx.jobmatch.tracking.domain.TrackingState;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Profile("api")
@RestController
@RequestMapping("/api/v1/me")
public class TrackingController {
    private final TrackingService tracking;
    public TrackingController(TrackingService tracking) { this.tracking = tracking; }

    @PostMapping("/job-impressions")
    ImpressionResponse impressions(@AuthenticationPrincipal AccountPrincipal principal,
                                   @RequestBody ImpressionRequest request) {
        return new ImpressionResponse(tracking.recordImpressions(principal.accountId(),
                request == null ? null : request.jobIds()));
    }

    @GetMapping("/job-activity")
    List<JobActivity> activity(@AuthenticationPrincipal AccountPrincipal principal,
                               @RequestParam List<UUID> jobId) {
        return tracking.activities(principal.accountId(), jobId);
    }

    @GetMapping("/jobs/{jobId}/tracking")
    ResponseEntity<TrackedJob> get(@AuthenticationPrincipal AccountPrincipal principal,
                                   @PathVariable UUID jobId) {
        return response(tracking.get(principal.accountId(), jobId));
    }

    @PutMapping("/jobs/{jobId}/tracking")
    ResponseEntity<TrackedJob> transition(@AuthenticationPrincipal AccountPrincipal principal,
                                          @PathVariable UUID jobId,
                                          @RequestHeader("If-Match") String ifMatch,
                                          @RequestHeader("Idempotency-Key") String idempotencyKey,
                                          @RequestBody TransitionRequest request) {
        if (request == null) throw new mx.jobmatch.tracking.application.TrackingExceptions.InvalidTrackingRequest("El estado es obligatorio.");
        return response(tracking.transition(principal.accountId(), jobId, request.state(), request.note(),
                version(ifMatch), idempotencyKey));
    }

    @DeleteMapping("/jobs/{jobId}/tracking")
    ResponseEntity<Void> allowRecommendation(@AuthenticationPrincipal AccountPrincipal principal,
                                              @PathVariable UUID jobId,
                                              @RequestHeader("If-Match") String ifMatch) {
        tracking.allowRecommendation(principal.accountId(), jobId, version(ifMatch));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tracking")
    List<TrackedJob> list(@AuthenticationPrincipal AccountPrincipal principal,
                          @RequestParam(required = false) List<TrackingState> state,
                          @RequestParam(defaultValue = "50") int limit) {
        return tracking.list(principal.accountId(), state, limit);
    }

    private static ResponseEntity<TrackedJob> response(TrackedJob tracked) {
        return ResponseEntity.ok().eTag(Long.toString(tracked.version())).body(tracked);
    }

    private static long version(String header) {
        try {
            String value = header.strip();
            if (value.startsWith("W/")) value = value.substring(2);
            return Long.parseLong(value.replace("\"", ""));
        } catch (RuntimeException invalid) {
            throw new mx.jobmatch.tracking.application.TrackingExceptions.InvalidTrackingRequest("If-Match no es válido.");
        }
    }

    public record ImpressionRequest(List<UUID> jobIds) {}
    public record ImpressionResponse(int recorded) {}
    public record TransitionRequest(TrackingState state, String note) {}
}
