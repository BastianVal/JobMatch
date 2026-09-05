package mx.jobmatch.discovery.application;

import mx.jobmatch.discovery.domain.JobSearchCriteria;
import mx.jobmatch.discovery.domain.SavedSearch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedSearchRepository {
    List<SavedSearch> findAll(UUID accountId);
    int count(UUID accountId);
    SavedSearch create(UUID accountId, String name, JobSearchCriteria criteria);
    Optional<SavedSearch> update(UUID accountId, UUID id, long expectedVersion, String name, JobSearchCriteria criteria);
    boolean delete(UUID accountId, UUID id, long expectedVersion);
    boolean exists(UUID accountId, UUID id);
}
