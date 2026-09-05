package mx.jobmatch.discovery.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record JobSearchCriteria(
        String query,
        List<UUID> roleFamilyIds,
        List<String> remoteModes,
        List<String> employmentTypes,
        String countryCode,
        String state,
        String city,
        BigDecimal minimumMonthlySalary,
        Integer publishedWithinDays,
        boolean excludeEmployers,
        Integer limit,
        String cursor) {
    public JobSearchCriteria {
        roleFamilyIds = roleFamilyIds == null ? List.of() : List.copyOf(roleFamilyIds);
        remoteModes = remoteModes == null ? List.of() : List.copyOf(remoteModes);
        employmentTypes = employmentTypes == null ? List.of() : List.copyOf(employmentTypes);
    }
}
