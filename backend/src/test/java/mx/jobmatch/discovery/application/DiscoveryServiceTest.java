package mx.jobmatch.discovery.application;

import mx.jobmatch.discovery.domain.CursorPage;
import mx.jobmatch.discovery.domain.JobSearchCriteria;
import mx.jobmatch.vacancies.domain.JobSummary;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DiscoveryServiceTest {
    JobSearchRepository jobs = mock(JobSearchRepository.class);
    SavedSearchRepository saved = mock(SavedSearchRepository.class);
    DiscoveryService service = new DiscoveryService(jobs, saved);

    @Test
    void normalizesSearchAndDefaultsToTwentyFiveResults() {
        UUID accountId = UUID.randomUUID();
        when(jobs.search(eq(accountId), any(), isNull())).thenReturn(new CursorPage<JobSummary>(List.of(), null));
        var input = new JobSearchCriteria("  Java backend  ", List.of(), List.of("remote"),
                List.of("full_time"), "mx", "Puebla", null, null, null, 30, false, null, null);

        service.search(accountId, input);

        ArgumentCaptor<JobSearchCriteria> criteria = ArgumentCaptor.forClass(JobSearchCriteria.class);
        verify(jobs).search(eq(accountId), criteria.capture(), isNull());
        assertThat(criteria.getValue().query()).isEqualTo("Java backend");
        assertThat(criteria.getValue().remoteModes()).containsExactly("REMOTE");
        assertThat(criteria.getValue().employmentTypes()).containsExactly("FULL_TIME");
        assertThat(criteria.getValue().countryCode()).isEqualTo("MX");
        assertThat(criteria.getValue().place()).isEqualTo("puebla");
        assertThat(criteria.getValue().limit()).isEqualTo(25);
    }

    @Test
    void enforcesSavedSearchLimit() {
        UUID accountId = UUID.randomUUID();
        when(saved.count(accountId)).thenReturn(20);

        assertThatThrownBy(() -> service.create(accountId, "Backend", emptyCriteria()))
                .isInstanceOf(DiscoveryExceptions.SavedSearchLimitReached.class);
        verify(saved, never()).create(any(), any(), any());
    }

    @Test
    void usesLegacyCityWhenRestoringAnExistingSavedSearch() {
        UUID accountId = UUID.randomUUID();
        when(jobs.search(eq(accountId), any(), isNull())).thenReturn(new CursorPage<JobSummary>(List.of(), null));

        service.search(accountId, new JobSearchCriteria(null, List.of(), List.of(), List.of(), "mx", null,
                null, "Puebla City", null, null, false, null, null));

        ArgumentCaptor<JobSearchCriteria> criteria = ArgumentCaptor.forClass(JobSearchCriteria.class);
        verify(jobs).search(eq(accountId), criteria.capture(), isNull());
        assertThat(criteria.getValue().place()).isEqualTo("puebla city");
    }

    private static JobSearchCriteria emptyCriteria() {
        return new JobSearchCriteria(null, List.of(), List.of(), List.of(), null, null, null, null,
                null, null, false, null, null);
    }
}
