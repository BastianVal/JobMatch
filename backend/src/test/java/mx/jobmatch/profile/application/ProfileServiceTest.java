package mx.jobmatch.profile.application;

import mx.jobmatch.catalog.application.CatalogQueryPort;
import mx.jobmatch.operations.application.OutboxPort;
import mx.jobmatch.operations.application.BackgroundTaskPort;
import mx.jobmatch.profile.domain.ProfessionalProfile;
import mx.jobmatch.profile.domain.ProfileDraft;
import mx.jobmatch.profile.domain.SkillDurationCalculator;
import mx.jobmatch.profile.domain.TrajectoryType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProfileServiceTest {
    ProfileRepository repository = mock(ProfileRepository.class);
    CatalogQueryPort catalog = mock(CatalogQueryPort.class);
    OutboxPort outbox = mock(OutboxPort.class);
    BackgroundTaskPort tasks = mock(BackgroundTaskPort.class);
    ProfileService service = new ProfileService(repository, catalog, outbox, tasks);

    @Test
    void saveCalculatesProjectionAndPublishesVersionedChange() {
        UUID accountId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID trajectoryId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        var trajectory = new ProfileDraft.TrajectoryItem(trajectoryId, TrajectoryType.EMPLOYMENT,
                "Backend developer", "Example", null, 2024, 1, 2024, 3, false);
        var skill = new ProfileDraft.Skill(skillId, UUID.randomUUID(), null, null, "ADVANCED", false,
                List.of(trajectoryId), 999, new java.math.BigDecimal("999"));
        var draft = new ProfileDraft("  Backend engineer  ", null, "México", "senior", List.of(), null,
                List.of("Ácme", "acme"), List.of(trajectory), List.of(), List.of(), List.of(), List.of(skill));
        when(catalog.currentVersion()).thenReturn(4);
        when(repository.replace(eq(accountId), eq(2L), any(), anyMap(), eq(4))).thenAnswer(invocation ->
                new ProfessionalProfile(profileId, 3, 4, invocation.getArgument(2)));

        var saved = service.replace(accountId, 2, draft);

        assertThat(saved.data().headline()).isEqualTo("Backend engineer");
        assertThat(saved.data().seniority()).isEqualTo("SENIOR");
        assertThat(saved.data().excludedEmployers()).containsExactly("Ácme");
        ArgumentCaptor<Map<UUID, SkillDurationCalculator.Projection>> projections = ArgumentCaptor.forClass(Map.class);
        verify(repository).replace(eq(accountId), eq(2L), any(), projections.capture(), eq(4));
        assertThat(projections.getValue().get(skillId).professionalMonths()).isEqualTo(3);
        assertThat(projections.getValue().get(skillId).weightedPracticalMonths()).isEqualByComparingTo("3.00");
        verify(outbox).append(eq("PROFILE"), eq(profileId), eq("ProfileChanged"), contains("\"profileVersion\":3"));
        verify(tasks).enqueue(eq("REFRESH_RECOMMENDATIONS"), contains(accountId.toString()),
                contains(profileId.toString()), any(), eq(5));
    }

    @Test
    void rejectsEvidenceThatIsNotPartOfOwnersSubmittedTrajectory() {
        var skill = new ProfileDraft.Skill(null, UUID.randomUUID(), null, null, null, false,
                List.of(UUID.randomUUID()), 0, java.math.BigDecimal.ZERO);
        var draft = new ProfileDraft(null, null, null, null, List.of(), null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(skill));

        assertThatThrownBy(() -> service.replace(UUID.randomUUID(), 0, draft))
                .isInstanceOf(ProfileExceptions.InvalidProfile.class);
        verifyNoInteractions(repository, outbox, tasks);
    }
}
