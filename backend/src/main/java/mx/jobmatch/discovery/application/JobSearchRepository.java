package mx.jobmatch.discovery.application;

import mx.jobmatch.discovery.domain.CursorPage;
import mx.jobmatch.discovery.domain.JobSearchCriteria;
import mx.jobmatch.vacancies.domain.JobDetail;
import mx.jobmatch.vacancies.domain.JobSummary;

import java.util.Optional;
import java.util.UUID;

public interface JobSearchRepository {
    CursorPage<JobSummary> search(UUID accountId, JobSearchCriteria criteria, SearchCursor cursor);
    Optional<JobDetail> find(UUID jobId);
}
