package mx.jobmatch.profile.application;

import mx.jobmatch.profile.domain.ProfessionalProfile;
import mx.jobmatch.profile.domain.ProfileDraft;
import mx.jobmatch.profile.domain.SkillDurationCalculator;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository {
    Optional<ProfessionalProfile> findByAccount(UUID accountId);
    ProfessionalProfile replace(UUID accountId, long expectedVersion, ProfileDraft draft,
                                Map<UUID, SkillDurationCalculator.Projection> projections, int catalogVersion);
}
