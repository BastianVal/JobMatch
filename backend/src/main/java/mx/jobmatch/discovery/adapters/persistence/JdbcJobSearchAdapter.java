package mx.jobmatch.discovery.adapters.persistence;

import mx.jobmatch.discovery.application.JobSearchRepository;
import mx.jobmatch.discovery.application.SearchCursor;
import mx.jobmatch.discovery.domain.CursorPage;
import mx.jobmatch.discovery.domain.JobSearchCriteria;
import mx.jobmatch.vacancies.domain.JobDetail;
import mx.jobmatch.vacancies.domain.JobSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcJobSearchAdapter implements JobSearchRepository {
    private final JdbcClient jdbc;

    public JdbcJobSearchAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public CursorPage<JobSummary> search(UUID accountId, JobSearchCriteria criteria, SearchCursor cursor) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("accountId", accountId);
        String relevance;
        StringBuilder filters = new StringBuilder("""
                WHERE job.status='ACTIVE'
                  AND NOT EXISTS (
                    SELECT 1 FROM tracking.user_job tracked
                    JOIN iam.account owner ON owner.id=tracked.account_id
                    WHERE owner.public_id=:accountId
                      AND tracked.canonical_job_id=job.id
                      AND tracked.state='DISCARDED'
                  )
                """);
        if (criteria.query() == null) {
            relevance = "0.000000::numeric";
        } else {
            relevance = "round(ts_rank_cd(document.search_vector, websearch_to_tsquery('spanish', unaccent(:query)))::numeric, 6)";
            filters.append(" AND document.search_vector @@ websearch_to_tsquery('spanish', unaccent(:query)) ");
            parameters.put("query", criteria.query());
        }
        appendIn(filters, parameters, "family.public_id", "role", criteria.roleFamilyIds());
        appendIn(filters, parameters, "document.remote_mode", "remote", criteria.remoteModes());
        appendIn(filters, parameters, "document.employment_type", "employment", criteria.employmentTypes());
        if (criteria.countryCode() != null) {
            filters.append(" AND document.country_code=:country ");
            parameters.put("country", criteria.countryCode());
        }
        if (criteria.state() != null) {
            filters.append(" AND document.state_normalized=:state ");
            parameters.put("state", criteria.state());
        }
        if (criteria.city() != null) {
            filters.append(" AND document.city_normalized=:city ");
            parameters.put("city", criteria.city());
        }
        if (criteria.minimumMonthlySalary() != null) {
            filters.append(" AND document.salary_max_monthly>=:salary ");
            parameters.put("salary", criteria.minimumMonthlySalary());
        }
        if (criteria.publishedWithinDays() != null) {
            filters.append(" AND job.published_at >= now() - (:days * interval '1 day') ");
            parameters.put("days", criteria.publishedWithinDays());
        }
        if (criteria.excludeEmployers()) {
            filters.append("""
                     AND NOT EXISTS (
                       SELECT 1 FROM iam.account owner
                       JOIN profile.professional_profile profile ON profile.account_id=owner.id
                       JOIN profile.excluded_employer excluded ON excluded.profile_id=profile.id
                       WHERE owner.public_id=:accountId AND excluded.employer_name_normalized=employer.name_normalized)
                    """);
        }
        String cursorFilter = "";
        if (cursor != null) {
            cursorFilter = """
                    WHERE (relevance < :cursorRelevance)
                       OR (relevance = :cursorRelevance AND published_at < :cursorPublished)
                       OR (relevance = :cursorRelevance AND published_at = :cursorPublished AND public_id < :cursorId)
                    """;
            parameters.put("cursorRelevance", cursor.relevance());
            parameters.put("cursorPublished", java.sql.Timestamp.from(cursor.publishedAt()));
            parameters.put("cursorId", cursor.jobId());
        }
        parameters.put("resultLimit", criteria.limit() + 1);
        String sql = """
                WITH ranked AS (
                  SELECT job.public_id, job.title, employer.canonical_name employer_name,
                         family.public_id role_family_id, family.display_name role_family,
                         job.seniority, job.remote_mode, job.employment_type,
                         job.salary_min_monthly, job.salary_max_monthly, job.currency,
                         location.city_name, location.state_name, document.country_code,
                         job.published_at, %s relevance
                  FROM jobs.job_search_document document
                  JOIN jobs.canonical_job job ON job.id=document.canonical_job_id
                  JOIN jobs.employer employer ON employer.id=job.employer_id
                  LEFT JOIN catalog.role_family family ON family.id=job.role_family_id
                  LEFT JOIN LATERAL (
                    SELECT city_name, state_name FROM jobs.job_location
                    WHERE canonical_job_id=job.id ORDER BY id LIMIT 1
                  ) location ON true
                  %s
                )
                SELECT * FROM ranked
                %s
                ORDER BY relevance DESC, published_at DESC, public_id DESC
                LIMIT :resultLimit
                """.formatted(relevance, filters, cursorFilter);
        JdbcClient.StatementSpec statement = jdbc.sql(sql);
        for (var parameter : parameters.entrySet()) statement.param(parameter.getKey(), parameter.getValue());
        List<JobSummary> found = statement.query((rs, row) -> new JobSummary(
                rs.getObject("public_id", UUID.class), rs.getString("title"), rs.getString("employer_name"),
                rs.getObject("role_family_id", UUID.class), rs.getString("role_family"), rs.getString("seniority"),
                rs.getString("remote_mode"), rs.getString("employment_type"), rs.getBigDecimal("salary_min_monthly"),
                rs.getBigDecimal("salary_max_monthly"), strip(rs.getString("currency")), rs.getString("city_name"),
                rs.getString("state_name"), strip(rs.getString("country_code")),
                rs.getTimestamp("published_at").toInstant(), rs.getBigDecimal("relevance"))).list();
        boolean more = found.size() > criteria.limit();
        List<JobSummary> items = more ? List.copyOf(found.subList(0, criteria.limit())) : List.copyOf(found);
        String next = null;
        if (more) {
            JobSummary last = items.getLast();
            next = SearchCursor.encode(last.relevance(), last.publishedAt(), last.id());
        }
        return new CursorPage<>(items, next);
    }

    @Override
    public Optional<JobDetail> find(UUID jobId) {
        Optional<JobBase> base = jdbc.sql("""
                SELECT job.id internal_id, job.public_id, job.version, job.title, employer.canonical_name employer_name,
                       family.public_id role_family_id, family.display_name role_family, job.description,
                       job.seniority, job.remote_mode, job.employment_type, job.salary_min_monthly,
                       job.salary_max_monthly, job.currency, job.published_at, job.status
                FROM jobs.canonical_job job JOIN jobs.employer employer ON employer.id=job.employer_id
                LEFT JOIN catalog.role_family family ON family.id=job.role_family_id
                WHERE job.public_id=:id
                """).param("id", jobId).query((rs, row) -> new JobBase(
                rs.getLong("internal_id"), rs.getObject("public_id", UUID.class), rs.getLong("version"),
                rs.getString("title"), rs.getString("employer_name"), rs.getObject("role_family_id", UUID.class),
                rs.getString("role_family"), rs.getString("description"), rs.getString("seniority"),
                rs.getString("remote_mode"), rs.getString("employment_type"), rs.getBigDecimal("salary_min_monthly"),
                rs.getBigDecimal("salary_max_monthly"), strip(rs.getString("currency")),
                rs.getTimestamp("published_at").toInstant(), rs.getString("status"))).optional();
        return base.map(this::hydrate);
    }

    private JobDetail hydrate(JobBase base) {
        var locations = jdbc.sql("""
                SELECT public_id, country_code, state_name, city_name FROM jobs.job_location
                WHERE canonical_job_id=:id ORDER BY id
                """).param("id", base.internalId()).query((rs, row) -> new JobDetail.Location(
                rs.getObject("public_id", UUID.class), strip(rs.getString("country_code")),
                rs.getString("state_name"), rs.getString("city_name"))).list();
        var links = jdbc.sql("""
                SELECT posting.public_id, source.display_name, posting.original_url, link.link_type, link.preferred
                FROM jobs.job_source_link link JOIN jobs.source_posting posting ON posting.id=link.source_posting_id
                JOIN jobs.source source ON source.id=posting.source_id
                WHERE link.canonical_job_id=:id AND link.active ORDER BY link.preferred DESC, posting.id
                """).param("id", base.internalId()).query((rs, row) -> new JobDetail.SourceLink(
                rs.getObject("public_id", UUID.class), rs.getString("display_name"), rs.getString("original_url"),
                rs.getString("link_type"), rs.getBoolean("preferred"))).list();
        var requirements = jdbc.sql("""
                SELECT public_id, requirement_text, category, mandatory FROM jobs.job_requirement
                WHERE canonical_job_id=:id ORDER BY position, id
                """).param("id", base.internalId()).query((rs, row) -> new JobDetail.Requirement(
                rs.getObject("public_id", UUID.class), rs.getString("requirement_text"),
                rs.getString("category"), rs.getBoolean("mandatory"))).list();
        var skills = jdbc.sql("""
                SELECT requirement.public_id, skill.public_id skill_id, skill.display_name, requirement.priority,
                       requirement.evidence_text FROM jobs.job_skill_requirement requirement
                JOIN catalog.skill skill ON skill.id=requirement.skill_id
                WHERE requirement.canonical_job_id=:id ORDER BY requirement.priority, skill.display_name
                """).param("id", base.internalId()).query((rs, row) -> new JobDetail.SkillRequirement(
                rs.getObject("public_id", UUID.class), rs.getObject("skill_id", UUID.class),
                rs.getString("display_name"), rs.getString("priority"), rs.getString("evidence_text"))).list();
        return new JobDetail(base.id(), base.version(), base.title(), base.employer(), base.roleFamilyId(),
                base.roleFamily(), base.description(), base.seniority(), base.remoteMode(), base.employmentType(),
                base.salaryMin(), base.salaryMax(), base.currency(), base.publishedAt(), base.status(),
                locations, links, requirements, skills);
    }

    private static void appendIn(StringBuilder sql, Map<String, Object> parameters, String column,
                                 String prefix, List<?> values) {
        if (values.isEmpty()) return;
        sql.append(" AND ").append(column).append(" IN (");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sql.append(',');
            String name = prefix + i;
            sql.append(':').append(name);
            parameters.put(name, values.get(i));
        }
        sql.append(") ");
    }

    private static String strip(String value) { return value == null ? null : value.strip(); }

    private record JobBase(long internalId, UUID id, long version, String title, String employer,
                           UUID roleFamilyId, String roleFamily, String description, String seniority,
                           String remoteMode, String employmentType, BigDecimal salaryMin, BigDecimal salaryMax,
                           String currency, java.time.Instant publishedAt, String status) {}
}
