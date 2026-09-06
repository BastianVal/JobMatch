package mx.jobmatch.catalog.application;

import java.util.Optional;
import java.util.UUID;

public interface CatalogRoleResolver {
    Optional<Resolution> resolve(String title);

    record Resolution(UUID roleFamilyId, String seniority) {}
}
