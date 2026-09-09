package mx.jobmatch.discovery.application;

import mx.jobmatch.discovery.domain.CursorPage;
import mx.jobmatch.discovery.domain.JobSearchCriteria;
import mx.jobmatch.discovery.domain.SavedSearch;
import mx.jobmatch.vacancies.domain.JobDetail;
import mx.jobmatch.vacancies.domain.JobSummary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static mx.jobmatch.discovery.application.DiscoveryExceptions.*;

@Profile("api")
@Service
public class DiscoveryService {
    private static final Set<String> REMOTE_MODES = Set.of("REMOTE", "HYBRID", "ONSITE");
    private static final Set<String> EMPLOYMENT_TYPES = Set.of("FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP");
    private final JobSearchRepository jobs;
    private final SavedSearchRepository savedSearches;

    public DiscoveryService(JobSearchRepository jobs, SavedSearchRepository savedSearches) {
        this.jobs = jobs;
        this.savedSearches = savedSearches;
    }

    @Transactional(readOnly = true)
    public CursorPage<JobSummary> search(UUID accountId, JobSearchCriteria input) {
        JobSearchCriteria criteria = normalize(input, true);
        return jobs.search(accountId, criteria, SearchCursor.decode(criteria.cursor()));
    }

    @Transactional(readOnly = true)
    public JobDetail get(UUID jobId) {
        return jobs.find(jobId).orElseThrow(ResourceNotFound::new);
    }

    @Transactional(readOnly = true)
    public List<SavedSearch> savedSearches(UUID accountId) {
        return savedSearches.findAll(accountId);
    }

    @Transactional
    public SavedSearch create(UUID accountId, String name, JobSearchCriteria input) {
        if (savedSearches.count(accountId) >= 20) throw new SavedSearchLimitReached();
        return savedSearches.create(accountId, name(name), normalize(input, false));
    }

    @Transactional
    public SavedSearch update(UUID accountId, UUID id, long expectedVersion, String name, JobSearchCriteria input) {
        var updated = savedSearches.update(accountId, id, expectedVersion, name(name), normalize(input, false));
        if (updated.isPresent()) return updated.get();
        if (savedSearches.exists(accountId, id)) throw new VersionConflict();
        throw new ResourceNotFound();
    }

    @Transactional
    public void delete(UUID accountId, UUID id, long expectedVersion) {
        if (savedSearches.delete(accountId, id, expectedVersion)) return;
        if (savedSearches.exists(accountId, id)) throw new VersionConflict();
        throw new ResourceNotFound();
    }

    private static JobSearchCriteria normalize(JobSearchCriteria input, boolean includePage) {
        if (input == null) input = new JobSearchCriteria(null, List.of(), List.of(), List.of(), null,
                null, null, null, null, null, false, null, null);
        if (input.roleFamilyIds().size() > 10) throw new InvalidSearch("Solo se permiten 10 roles por búsqueda.");
        if (new HashSet<>(input.roleFamilyIds()).size() != input.roleFamilyIds().size())
            throw new InvalidSearch("Los roles de búsqueda deben ser únicos.");
        List<String> remote = choices(input.remoteModes(), REMOTE_MODES, "modalidad");
        List<String> employment = choices(input.employmentTypes(), EMPLOYMENT_TYPES, "tipo de empleo");
        String country = text(input.countryCode(), 2);
        if (country != null && country.length() != 2) throw new InvalidSearch("El país debe usar código ISO de dos letras.");
        country = country == null ? null : country.toUpperCase(Locale.ROOT);
        if (input.minimumMonthlySalary() != null && input.minimumMonthlySalary().signum() < 0)
            throw new InvalidSearch("El salario mínimo no puede ser negativo.");
        if (input.publishedWithinDays() != null
                && (input.publishedWithinDays() < 1 || input.publishedWithinDays() > 365))
            throw new InvalidSearch("La antigüedad debe estar entre 1 y 365 días.");
        int limit = includePage ? (input.limit() == null ? 25 : input.limit()) : 25;
        if (limit < 1 || limit > 25) throw new InvalidSearch("El límite debe estar entre 1 y 25.");
        return new JobSearchCriteria(text(input.query(), 200), input.roleFamilyIds(), remote, employment,
                country, normalizedLookup(firstPresent(input.place(), input.city(), input.state()), 160), null, null,
                input.minimumMonthlySalary(), input.publishedWithinDays(), input.excludeEmployers(), limit,
                includePage ? input.cursor() : null);
    }

    /** The old state/city parameters are accepted only to keep existing saved searches usable. */
    private static String firstPresent(String first, String second, String third) {
        return text(first, 160) != null ? first : text(second, 160) != null ? second : third;
    }

    private static List<String> choices(List<String> values, Set<String> allowed, String label) {
        List<String> normalized = values.stream().map(value -> {
            String item = text(value, 30);
            if (item == null || !allowed.contains(item.toUpperCase(Locale.ROOT)))
                throw new InvalidSearch("Valor de " + label + " no válido.");
            return item.toUpperCase(Locale.ROOT);
        }).distinct().toList();
        if (normalized.size() != values.size()) throw new InvalidSearch("Los filtros no deben repetirse.");
        return normalized;
    }

    private static String name(String value) {
        String name = text(value, 120);
        if (name == null) throw new InvalidSearch("La búsqueda guardada requiere nombre.");
        return name;
    }

    private static String text(String value, int max) {
        if (value == null) return null;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
        if (normalized.codePointCount(0, normalized.length()) > max)
            throw new InvalidSearch("Un campo excede la longitud permitida.");
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizedLookup(String value, int max) {
        String text = text(value, max);
        if (text == null) return null;
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
