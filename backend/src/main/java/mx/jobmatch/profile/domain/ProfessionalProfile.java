package mx.jobmatch.profile.domain;

import java.util.UUID;

public record ProfessionalProfile(UUID id, long version, int catalogVersion, ProfileDraft data) {}
