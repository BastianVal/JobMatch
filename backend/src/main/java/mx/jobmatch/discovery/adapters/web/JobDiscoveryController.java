package mx.jobmatch.discovery.adapters.web;

import mx.jobmatch.discovery.application.DiscoveryService;
import mx.jobmatch.discovery.domain.CursorPage;
import mx.jobmatch.discovery.domain.JobSearchCriteria;
import mx.jobmatch.identity.adapters.security.AccountPrincipal;
import mx.jobmatch.vacancies.domain.JobDetail;
import mx.jobmatch.vacancies.domain.JobSummary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Profile("api")
@RestController
@RequestMapping("/api/v1/jobs")
public class JobDiscoveryController {
    private final DiscoveryService discovery;

    public JobDiscoveryController(DiscoveryService discovery) { this.discovery = discovery; }

    @GetMapping("/search")
    CursorPage<JobSummary> search(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<UUID> roleFamilyId,
            @RequestParam(required = false) List<String> remoteMode,
            @RequestParam(required = false) List<String> employmentType,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) BigDecimal minimumMonthlySalary,
            @RequestParam(required = false) Integer publishedWithinDays,
            @RequestParam(defaultValue = "false") boolean excludeEmployers,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return discovery.search(principal.accountId(), new JobSearchCriteria(q, roleFamilyId, remoteMode,
                employmentType, countryCode, state, city, minimumMonthlySalary, publishedWithinDays,
                excludeEmployers, limit, cursor));
    }

    @GetMapping("/{id}")
    JobDetail get(@PathVariable UUID id) { return discovery.get(id); }
}
