package mx.jobmatch.ingestion.adapters.persistence;

import mx.jobmatch.catalog.application.CatalogRoleResolver;
import mx.jobmatch.ingestion.application.IngestionRepository;
import mx.jobmatch.ingestion.domain.ConnectorQuery;
import mx.jobmatch.ingestion.domain.DeduplicationPolicy;
import mx.jobmatch.ingestion.domain.JobRefresh;
import mx.jobmatch.ingestion.domain.NormalizedPosting;
import mx.jobmatch.ingestion.domain.PostingNormalizer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static mx.jobmatch.ingestion.application.IngestionExceptions.InvalidRefresh;

@Repository
public class JdbcIngestionAdapter implements IngestionRepository {
    private static final List<String> SOURCES = List.of("JOOBLE", "ADZUNA", "GREENHOUSE", "LEVER", "ASHBY");
    private final JdbcClient jdbc;
    private final CatalogRoleResolver roles;

    public JdbcIngestionAdapter(JdbcClient jdbc, CatalogRoleResolver roles) {
        this.jdbc = jdbc;
        this.roles = roles;
    }

    @Override
    public Schedule schedule(UUID accountId, UUID roleFamilyId, String roleQuery, String locationQuery, Instant now) {
        Long account = jdbc.sql("SELECT id FROM iam.account WHERE public_id=:id AND status='ACTIVE' FOR UPDATE")
                .param("id", accountId).query(Long.class).optional()
                .orElseThrow(() -> new InvalidRefresh("La cuenta no está activa."));
        Long role = roleFamilyId == null ? null : jdbc.sql("SELECT id FROM catalog.role_family WHERE public_id=:id AND active")
                .param("id", roleFamilyId).query(Long.class).optional()
                .orElseThrow(() -> new InvalidRefresh("La familia de rol no existe."));
        List<QuerySource> scheduled = new ArrayList<>();
        Instant nextAllowed = now.plusSeconds(900);
        for (String sourceKey : SOURCES) {
            String admittedLocation = sourceKey.equals("JOOBLE") || sourceKey.equals("ADZUNA") ? locationQuery : null;
            String signature = PostingNormalizer.sha256(sourceKey + "|" + value(roleQuery) + "|" + value(admittedLocation));
            QueryRow query = jdbc.sql("""
                    INSERT INTO ingestion.connector_query(public_id, source_id, query_signature, role_query, location_query)
                    SELECT :id, id, :signature, :role, :location FROM jobs.source WHERE source_key=:source
                    ON CONFLICT (source_id, query_signature) DO UPDATE SET enabled=true
                    RETURNING id, public_id, last_requested_at
                    """).param("id", UUID.randomUUID()).param("signature", signature).param("role", roleQuery)
                    .param("location", admittedLocation).param("source", sourceKey)
                    .query((rs, n) -> new QueryRow(rs.getLong("id"), rs.getObject("public_id", UUID.class),
                            instant(rs.getTimestamp("last_requested_at")))).single();
            jdbc.sql("""
                    INSERT INTO ingestion.query_demand(connector_query_id, account_id, role_family_id)
                    VALUES (:query, :account, :role)
                    ON CONFLICT (connector_query_id, account_id) DO UPDATE SET
                      role_family_id=excluded.role_family_id, last_demanded_at=now()
                    """).param("query", query.internalId()).param("account", account).param("role", role).update();
            Instant allowed = query.lastRequestedAt() == null ? now : query.lastRequestedAt().plusSeconds(900);
            if (!allowed.isAfter(now)) {
                jdbc.sql("UPDATE ingestion.connector_query SET last_requested_at=:now, next_scheduled_at=:next WHERE id=:id")
                        .param("now", Timestamp.from(now)).param("next", Timestamp.from(nextAllowed))
                        .param("id", query.internalId()).update();
                scheduled.add(new QuerySource(new ConnectorQuery(query.id(), roleQuery, admittedLocation, null), sourceKey));
            } else if (allowed.isBefore(nextAllowed)) nextAllowed = allowed;
        }
        UUID refreshId = UUID.randomUUID();
        JobRefresh refresh = jdbc.sql("""
                INSERT INTO ingestion.job_refresh(public_id, account_id, request_signature, status,
                    scheduled_connectors, next_allowed_at)
                VALUES (:id, :account, :signature, :status, :count, :next)
                RETURNING public_id, status, scheduled_connectors, completed_connectors,
                    failed_connectors, next_allowed_at, created_at
                """).param("id", refreshId).param("account", account)
                .param("signature", PostingNormalizer.sha256(value(roleQuery) + "|" + value(locationQuery)))
                .param("status", scheduled.isEmpty() ? "COOLDOWN" : "SCHEDULED").param("count", scheduled.size())
                .param("next", Timestamp.from(nextAllowed)).query(this::mapRefresh).single();
        return new Schedule(refresh, scheduled);
    }

    @Override
    public Optional<JobRefresh> findRefresh(UUID accountId, UUID refreshId) {
        return jdbc.sql("""
                SELECT refresh.public_id, refresh.status, refresh.scheduled_connectors, refresh.completed_connectors,
                       refresh.failed_connectors, refresh.next_allowed_at, refresh.created_at
                FROM ingestion.job_refresh refresh JOIN iam.account account ON account.id=refresh.account_id
                WHERE account.public_id=:account AND refresh.public_id=:id
                """).param("account", accountId).param("id", refreshId).query(this::mapRefresh).optional();
    }

    @Override
    public Optional<QuerySource> findQuery(UUID queryId) {
        return jdbc.sql("""
                SELECT query.public_id, query.role_query, query.location_query, source.source_key
                FROM ingestion.connector_query query JOIN jobs.source source ON source.id=query.source_id
                WHERE query.public_id=:id AND query.enabled
                """).param("id", queryId).query((rs, n) -> new QuerySource(new ConnectorQuery(
                        rs.getObject("public_id", UUID.class), rs.getString("role_query"),
                        rs.getString("location_query"), null), rs.getString("source_key"))).optional();
    }

    @Override
    public List<QuerySource> findDueQueries(int limit) {
        return jdbc.sql("""
                SELECT query.public_id, query.role_query, query.location_query, source.source_key
                FROM ingestion.connector_query query JOIN jobs.source source ON source.id=query.source_id
                WHERE query.enabled AND query.next_scheduled_at<=now()
                  AND EXISTS (SELECT 1 FROM ingestion.query_demand demand WHERE demand.connector_query_id=query.id)
                ORDER BY query.next_scheduled_at, query.id LIMIT :limit
                """).param("limit", limit).query((rs, n) -> new QuerySource(new ConnectorQuery(
                        rs.getObject("public_id", UUID.class), rs.getString("role_query"),
                        rs.getString("location_query"), null), rs.getString("source_key"))).list();
    }

    @Override
    public void postponeQuery(UUID queryId, Instant nextScheduledAt) {
        jdbc.sql("UPDATE ingestion.connector_query SET next_scheduled_at=:next WHERE public_id=:id AND next_scheduled_at<=now()")
                .param("next", Timestamp.from(nextScheduledAt)).param("id", queryId).update();
    }

    @Override
    public UUID startRun(UUID queryId, UUID refreshId) {
        if (refreshId == null) return jdbc.sql("""
                INSERT INTO ingestion.sync_run(public_id, connector_query_id, status)
                SELECT :id, query.id, 'RUNNING' FROM ingestion.connector_query query WHERE query.public_id=:query
                RETURNING public_id
                """).param("id", UUID.randomUUID()).param("query", queryId).query(UUID.class).single();
        return jdbc.sql("""
                INSERT INTO ingestion.sync_run(public_id, connector_query_id, refresh_id, status)
                SELECT :id, query.id, refresh.id, 'RUNNING' FROM ingestion.connector_query query
                JOIN ingestion.job_refresh refresh ON refresh.public_id=:refresh WHERE query.public_id=:query
                RETURNING public_id
                """).param("id", UUID.randomUUID()).param("refresh", refreshId).param("query", queryId)
                .query(UUID.class).single();
    }

    @Override
    @Transactional
    public boolean acquireRequestPermit(String sourceKey, UUID queryId, UUID leaseOwner, Instant now) {
        jdbc.sql("""
                INSERT INTO ingestion.source_runtime_state(source_id)
                SELECT id FROM jobs.source WHERE source_key=:source ON CONFLICT DO NOTHING
                """).param("source", sourceKey).update();
        boolean queryAcquired = jdbc.sql("""
                UPDATE ingestion.connector_query SET execution_lease_owner=:owner,
                  execution_lease_until=:leaseUntil
                WHERE public_id=:query AND (execution_lease_until IS NULL OR execution_lease_until<=:now
                  OR execution_lease_owner=:owner)
                RETURNING id
                """).param("owner", leaseOwner).param("leaseUntil", Timestamp.from(now.plusSeconds(60)))
                .param("query", queryId).param("now", Timestamp.from(now))
                .query(Long.class).optional().isPresent();
        if (!queryAcquired) return false;
        boolean sourceAcquired = jdbc.sql("""
                UPDATE ingestion.source_runtime_state state SET
                  quota_window_start=CASE WHEN quota_window_start<current_date THEN current_date ELSE quota_window_start END,
                  requests_in_window=CASE WHEN quota_window_start<current_date THEN 1 ELSE requests_in_window+1 END,
                  execution_lease_owner=:owner, execution_lease_until=:leaseUntil,
                  updated_at=now()
                FROM jobs.source source WHERE source.id=state.source_id AND source.source_key=:source
                  AND (state.circuit_open_until IS NULL OR state.circuit_open_until<=:now)
                  AND (state.execution_lease_until IS NULL OR state.execution_lease_until<=:now
                    OR state.execution_lease_owner=:owner)
                  AND (source.quota_per_day IS NULL OR
                       (CASE WHEN state.quota_window_start<current_date THEN 0 ELSE state.requests_in_window END)<source.quota_per_day)
                RETURNING state.source_id
                """).param("source", sourceKey).param("owner", leaseOwner)
                .param("leaseUntil", Timestamp.from(now.plusSeconds(60))).param("now", Timestamp.from(now))
                .query(Long.class).optional().isPresent();
        if (!sourceAcquired) releaseRequestPermit(sourceKey, queryId, leaseOwner);
        return sourceAcquired;
    }

    @Override
    public void releaseRequestPermit(String sourceKey, UUID queryId, UUID leaseOwner) {
        jdbc.sql("""
                UPDATE ingestion.connector_query SET execution_lease_owner=NULL, execution_lease_until=NULL
                WHERE public_id=:query AND execution_lease_owner=:owner
                """).param("query", queryId).param("owner", leaseOwner).update();
        jdbc.sql("""
                UPDATE ingestion.source_runtime_state state
                SET execution_lease_owner=NULL, execution_lease_until=NULL, updated_at=now()
                FROM jobs.source source WHERE source.id=state.source_id AND source.source_key=:source
                  AND state.execution_lease_owner=:owner
                """).param("source", sourceKey).param("owner", leaseOwner).update();
    }

    @Override public void sourceSucceeded(String sourceKey) {
        jdbc.sql("""
                UPDATE ingestion.source_runtime_state state SET consecutive_failures=0, circuit_open_until=NULL, updated_at=now()
                FROM jobs.source source WHERE source.id=state.source_id AND source.source_key=:source
                """).param("source", sourceKey).update();
    }

    @Override public void sourceFailed(String sourceKey, Instant now) {
        jdbc.sql("""
                UPDATE ingestion.source_runtime_state state SET consecutive_failures=consecutive_failures+1,
                  circuit_open_until=CASE WHEN consecutive_failures+1>=5 THEN :openUntil ELSE circuit_open_until END,
                  updated_at=now() FROM jobs.source source
                WHERE source.id=state.source_id AND source.source_key=:source
                """).param("source", sourceKey).param("openUntil", Timestamp.from(now.plusSeconds(900))).update();
    }

    @Override public void finishRun(UUID runId, Counts counts, boolean complete) {
        if (complete) applyCompleteSnapshot(runId);
        jdbc.sql("""
                UPDATE ingestion.sync_run SET status='COMPLETED', fetched_count=:fetched, created_count=:created,
                  updated_count=:updated, linked_count=:linked, kept_separate_count=:separate, error_count=:errors,
                  complete_response=:complete, finished_at=now() WHERE public_id=:id
                """).param("fetched", counts.fetched()).param("created", counts.created()).param("updated", counts.updated())
                .param("linked", counts.linked()).param("separate", counts.keptSeparate()).param("errors", counts.errors())
                .param("complete", complete).param("id", runId).update();
    }
    @Override public void failRun(UUID runId, String code) {
        jdbc.sql("UPDATE ingestion.sync_run SET status='FAILED', safe_error_code=:code, finished_at=now() WHERE public_id=:id")
                .param("code", code).param("id", runId).update();
    }
    @Override public void itemFailed(UUID runId, String hash, String code) {
        jdbc.sql("""
                INSERT INTO ingestion.sync_item_error(sync_run_id, source_reference_hash, safe_error_code)
                SELECT id, :hash, :code FROM ingestion.sync_run WHERE public_id=:id
                """).param("hash", hash).param("code", code).param("id", runId).update();
    }
    @Override public void connectorCompleted(UUID refreshId) { progress(refreshId, true); }
    @Override public void connectorFailed(UUID refreshId) { progress(refreshId, false); }

    private void progress(UUID refreshId, boolean success) {
        jdbc.sql("""
                UPDATE ingestion.job_refresh SET
                  completed_connectors=completed_connectors+:completed,
                  failed_connectors=failed_connectors+:failed,
                  status=CASE
                    WHEN completed_connectors+:completed + failed_connectors+:failed < scheduled_connectors THEN 'SCHEDULED'
                    WHEN failed_connectors+:failed=0 THEN 'COMPLETED'
                    WHEN completed_connectors+:completed=0 THEN 'FAILED' ELSE 'PARTIAL' END,
                  updated_at=now() WHERE public_id=:id
                """).param("completed", success ? 1 : 0).param("failed", success ? 0 : 1).param("id", refreshId).update();
    }

    @Override
    public UpsertResult upsert(UUID runId, NormalizedPosting p) {
        SourceRow source = jdbc.sql("SELECT id, public_id FROM jobs.source WHERE source_key=:key")
                .param("key", p.sourceKey()).query((rs,n)->new SourceRow(rs.getLong("id"),rs.getObject("public_id",UUID.class))).single();
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:identity, 0))")
                .param("identity", p.identityKey()).query((rs, row) -> 0).single();
        Optional<ExistingLink> existing = jdbc.sql("""
                SELECT posting.id posting_id, job.id job_id, job.public_id job_public_id
                FROM jobs.source_posting posting JOIN jobs.job_source_link link ON link.source_posting_id=posting.id
                JOIN jobs.canonical_job job ON job.id=link.canonical_job_id
                WHERE posting.source_id=:source AND (posting.external_id=:external OR posting.normalized_url_hash=:urlHash)
                ORDER BY posting.id LIMIT 1
                """).param("source", source.id()).param("external", p.externalId()).param("urlHash", p.urlHash())
                .query((rs,n)->new ExistingLink(rs.getLong("posting_id"),rs.getLong("job_id"),rs.getObject("job_public_id",UUID.class))).optional();
        if (existing.isPresent()) {
            updatePosting(existing.get().postingId(), p);
            jdbc.sql("UPDATE jobs.job_source_link SET active=true WHERE source_posting_id=:posting")
                    .param("posting", existing.get().postingId()).update();
            updateClassification(existing.get().jobId(), p);
            rebuildSearch(existing.get().jobId());
            recordPayload(runId, source.id(), existing.get().postingId(), p);
            event(existing.get().jobPublicId(), "UPDATED");
            return new UpsertResult(existing.get().jobPublicId(), Outcome.UPDATED);
        }
        long employerId = jdbc.sql("""
                INSERT INTO jobs.employer(public_id, canonical_name, name_normalized) VALUES (:id,:name,:normalized)
                ON CONFLICT (name_normalized) DO UPDATE SET canonical_name=jobs.employer.canonical_name RETURNING id
                """).param("id", UUID.randomUUID()).param("name", p.employer()).param("normalized", p.employerNormalized())
                .query(Long.class).single();
        CandidateChoice choice = chooseCandidate(p, employerId);
        boolean merge = choice != null && choice.evaluation().decision()==DeduplicationPolicy.Decision.AUTO_MERGED;
        boolean uncertain = choice != null && choice.evaluation().decision()==DeduplicationPolicy.Decision.KEPT_SEPARATE;
        JobRow job = merge ? new JobRow(choice.candidate().internalId(), choice.publicId())
                : createJob(p, employerId, uncertain ? p.identityKey()+"|"+p.sourceKey()+"|"+p.externalId() : p.identityKey());
        long postingId = createPosting(source.id(), p);
        jdbc.sql("INSERT INTO jobs.job_source_link(canonical_job_id,source_posting_id,link_type,preferred) VALUES (:job,:posting,'AGGREGATOR',false)")
                .param("job", job.id()).param("posting", postingId).update();
        recordPayload(runId, source.id(), postingId, p);
        if (merge || uncertain) audit(postingId, choice, job.id(), merge ? "AUTO_MERGED" : "KEPT_SEPARATE");
        rebuildSearch(job.id());
        event(job.publicId(), merge ? "LINKED" : uncertain ? "KEPT_SEPARATE" : "CREATED");
        return new UpsertResult(job.publicId(), merge ? Outcome.LINKED : uncertain ? Outcome.KEPT_SEPARATE : Outcome.CREATED);
    }

    private CandidateChoice chooseCandidate(NormalizedPosting p, long employerId) {
        return jdbc.sql("""
                SELECT job.id, job.public_id, job.title_normalized, employer.name_normalized,
                       job.description, location.city_normalized, job.salary_max_monthly, job.published_at
                FROM jobs.canonical_job job JOIN jobs.employer employer ON employer.id=job.employer_id
                LEFT JOIN jobs.job_location location ON location.canonical_job_id=job.id
                WHERE job.employer_id=:employer AND job.published_at BETWEEN :fromDate AND :toDate
                ORDER BY job.published_at DESC LIMIT 20
                """).param("employer", employerId).param("fromDate", Timestamp.from(p.publishedAt().minusSeconds(30L*86400)))
                .param("toDate", Timestamp.from(p.publishedAt().plusSeconds(30L*86400)))
                .query((rs,n)-> {
                    var candidate = new DeduplicationPolicy.Candidate(rs.getLong("id"),rs.getString("title_normalized"),
                            rs.getString("name_normalized"),rs.getString("description"),rs.getString("city_normalized"),
                            rs.getBigDecimal("salary_max_monthly"),rs.getTimestamp("published_at").toInstant());
                    return new CandidateChoice(candidate, rs.getObject("public_id",UUID.class), DeduplicationPolicy.evaluate(p,candidate));
                }).list().stream().max(java.util.Comparator.comparingDouble(c->c.evaluation().score())).orElse(null);
    }

    private JobRow createJob(NormalizedPosting p, long employerId, String identityKey) {
        var resolution = roles.resolve(p.title()).orElse(null);
        Long role = resolution == null ? null : jdbc.sql("""
                SELECT role.id FROM catalog.role_family role
                JOIN catalog.catalog_version version ON version.id=role.catalog_version_id
                WHERE role.public_id=:id AND role.active AND version.status='PUBLISHED'
                """).param("id", resolution.roleFamilyId()).query(Long.class).optional().orElse(null);
        String seniority = p.seniority() != null ? p.seniority() : resolution == null ? null : resolution.seniority();
        JobRow job = jdbc.sql("""
                INSERT INTO jobs.canonical_job(public_id,identity_key,employer_id,role_family_id,title,title_normalized,
                  description,seniority,remote_mode,employment_type,salary_min_monthly,salary_max_monthly,currency,published_at,status)
                VALUES (:id,:identity,:employer,:role,:title,:titleKey,:description,:seniority,:remote,:employment,
                  :salaryMin,:salaryMax,:currency,:published,'ACTIVE') RETURNING id,public_id
                """).param("id",UUID.randomUUID()).param("identity",identityKey).param("employer",employerId).param("role",role)
                .param("title",p.title()).param("titleKey",p.titleNormalized()).param("description",p.description())
                .param("seniority",seniority).param("remote",p.remoteMode()).param("employment",p.employmentType())
                .param("salaryMin",p.salaryMinMonthly()).param("salaryMax",p.salaryMaxMonthly()).param("currency",p.currency())
                .param("published",Timestamp.from(p.publishedAt())).query((rs,n)->new JobRow(rs.getLong("id"),rs.getObject("public_id",UUID.class))).single();
        jdbc.sql("""
                INSERT INTO jobs.job_location(public_id,canonical_job_id,country_code,state_name,state_normalized,city_name,city_normalized)
                VALUES (:id,:job,:country,:state,:stateKey,:city,:cityKey)
                """).param("id",UUID.randomUUID()).param("job",job.id()).param("country",p.countryCode()).param("state",p.state())
                .param("stateKey",p.stateNormalized()).param("city",p.city()).param("cityKey",p.cityNormalized()).update();
        jdbc.sql("INSERT INTO jobs.job_requirement(public_id,canonical_job_id,requirement_text,category,mandatory,position) VALUES (:id,:job,:text,'OTHER',false,1)")
                .param("id",UUID.randomUUID()).param("job",job.id()).param("text",p.description().substring(0,Math.min(500,p.description().length()))).update();
        inferSkill(job.id(),p);
        return job;
    }

    private void updateClassification(long jobId, NormalizedPosting posting) {
        var resolution = roles.resolve(posting.title()).orElse(null);
        Long role = resolution == null ? null : jdbc.sql("""
                SELECT role.id FROM catalog.role_family role
                JOIN catalog.catalog_version version ON version.id=role.catalog_version_id
                WHERE role.public_id=:id AND role.active AND version.status='PUBLISHED'
                """).param("id", resolution.roleFamilyId()).query(Long.class).optional().orElse(null);
        String seniority = posting.seniority() != null ? posting.seniority()
                : resolution == null ? null : resolution.seniority();
        jdbc.sql("""
                UPDATE jobs.canonical_job
                SET role_family_id=:role, seniority=:seniority, status='ACTIVE', updated_at=now(), version=version+1
                WHERE id=:job
                """).param("role", role).param("seniority", seniority).param("job", jobId).update();
    }

    private void inferSkill(long jobId, NormalizedPosting p) {
        String text=(p.titleNormalized()+" "+PostingNormalizer.key(p.description()));
        String skill=text.contains("java")?"12000000-0000-0000-0000-000000000001":text.contains("docker")?"12000000-0000-0000-0000-000000000007":null;
        if(skill!=null) jdbc.sql("""
                INSERT INTO jobs.job_skill_requirement(public_id,canonical_job_id,skill_id,priority,evidence_text)
                SELECT :id,:job,id,'REQUIRED',:evidence FROM catalog.skill WHERE public_id=:skill
                """).param("id",UUID.randomUUID()).param("job",jobId).param("evidence",p.description().substring(0,Math.min(300,p.description().length())))
                .param("skill",UUID.fromString(skill)).update();
    }
    private long createPosting(long sourceId, NormalizedPosting p) {
        return jdbc.sql("""
                INSERT INTO jobs.source_posting(public_id,source_id,external_id,original_url,normalized_url_hash,payload,published_at,status)
                VALUES (:id,:source,:external,:url,:hash,CAST(:payload AS jsonb),:published,'ACTIVE') RETURNING id
                """).param("id",UUID.randomUUID()).param("source",sourceId).param("external",p.externalId()).param("url",p.url())
                .param("hash",p.urlHash()).param("payload",p.payload()).param("published",Timestamp.from(p.publishedAt())).query(Long.class).single();
    }
    private void updatePosting(long id, NormalizedPosting p) {
        jdbc.sql("UPDATE jobs.source_posting SET last_seen_at=now(),payload=CAST(:payload AS jsonb),status='ACTIVE' WHERE id=:id")
                .param("payload",p.payload()).param("id",id).update();
    }
    private void recordPayload(UUID runId,long source,long posting,NormalizedPosting p){
        jdbc.sql("INSERT INTO ingestion.source_payload(source_id,source_posting_id,payload_hash,payload) VALUES (:source,:posting,:hash,CAST(:payload AS jsonb))")
                .param("source",source).param("posting",posting).param("hash",p.payloadHash()).param("payload",p.payload()).update();
        jdbc.sql("""
                INSERT INTO ingestion.query_posting_observation(
                    connector_query_id, source_posting_id, last_seen_run_id, first_seen_at, last_seen_at, consecutive_absences)
                SELECT run.connector_query_id, :posting, run.id, now(), now(), 0
                FROM ingestion.sync_run run WHERE run.public_id=:run
                ON CONFLICT (connector_query_id, source_posting_id) DO UPDATE SET
                    last_seen_run_id=excluded.last_seen_run_id, last_seen_at=excluded.last_seen_at, consecutive_absences=0
                """).param("posting",posting).param("run",runId).update();
    }

    private void applyCompleteSnapshot(UUID runId) {
        jdbc.sql("""
                UPDATE ingestion.query_posting_observation observation
                SET consecutive_absences=consecutive_absences+1
                FROM ingestion.sync_run run
                WHERE run.public_id=:run AND observation.connector_query_id=run.connector_query_id
                  AND observation.last_seen_run_id<>run.id
                """).param("run",runId).update();
        jdbc.sql("""
                UPDATE jobs.source_posting posting SET status='MISSING'
                FROM ingestion.query_posting_observation observation
                JOIN ingestion.sync_run run ON run.connector_query_id=observation.connector_query_id
                WHERE run.public_id=:run AND posting.id=observation.source_posting_id
                  AND observation.consecutive_absences>=3
                  AND observation.last_seen_at<now()-interval '7 days'
                """).param("run",runId).update();
        jdbc.sql("""
                UPDATE jobs.job_source_link link SET active=false
                FROM jobs.source_posting posting
                WHERE link.source_posting_id=posting.id AND posting.status IN ('MISSING','CLOSED')
                """).update();
        jdbc.sql("""
                UPDATE jobs.canonical_job job SET status='SUSPECTED_EXPIRED', updated_at=now(), version=version+1
                WHERE job.status='ACTIVE' AND NOT EXISTS (
                    SELECT 1 FROM jobs.job_source_link link WHERE link.canonical_job_id=job.id AND link.active)
                """).update();
    }
    private void rebuildSearch(long jobId){jdbc.sql("""
            INSERT INTO jobs.job_search_document(canonical_job_id,role_family_id,country_code,state_normalized,city_normalized,remote_mode,employment_type,salary_max_monthly,search_vector,projected_job_version)
            SELECT job.id,job.role_family_id,location.country_code,location.state_normalized,location.city_normalized,job.remote_mode,job.employment_type,job.salary_max_monthly,
              setweight(to_tsvector('spanish',unaccent(job.title)),'A')||setweight(to_tsvector('spanish',unaccent(employer.canonical_name)),'B')||setweight(to_tsvector('spanish',unaccent(job.description)),'C'),job.version
            FROM jobs.canonical_job job JOIN jobs.employer employer ON employer.id=job.employer_id JOIN jobs.job_location location ON location.canonical_job_id=job.id WHERE job.id=:id
            ON CONFLICT(canonical_job_id) DO UPDATE SET search_vector=excluded.search_vector,projected_job_version=excluded.projected_job_version,updated_at=now()
            """).param("id",jobId).update();}
    private void audit(long posting,CandidateChoice choice,long job,String decision){jdbc.sql("INSERT INTO jobs.job_merge_audit(source_posting_id,candidate_job_id,resolved_job_id,decision,similarity_score,algorithm_version,reasons) VALUES (:posting,:candidate,:job,:decision,:score,:version,CAST(:reasons AS jsonb))").param("posting",posting).param("candidate",choice.candidate().internalId()).param("job",job).param("decision",decision).param("score",BigDecimal.valueOf(choice.evaluation().score())).param("version",DeduplicationPolicy.VERSION).param("reasons","{\"title\":"+choice.evaluation().titleScore()+",\"employer\":"+choice.evaluation().employerScore()+"}").update();}
    private void event(UUID job,String outcome){jdbc.sql("INSERT INTO ops.outbox_event(public_id,aggregate_type,aggregate_public_id,event_type,payload) VALUES (:id,'JOB',:job,'JobChanged',CAST(:payload AS jsonb))").param("id",UUID.randomUUID()).param("job",job).param("payload","{\"jobId\":\""+job+"\",\"outcome\":\""+outcome+"\"}").update();}

    private JobRefresh mapRefresh(java.sql.ResultSet rs,int n)throws java.sql.SQLException{return new JobRefresh(rs.getObject("public_id",UUID.class),rs.getString("status"),rs.getInt("scheduled_connectors"),rs.getInt("completed_connectors"),rs.getInt("failed_connectors"),rs.getTimestamp("next_allowed_at").toInstant(),rs.getTimestamp("created_at").toInstant());}
    private static Instant instant(Timestamp timestamp){return timestamp==null?null:timestamp.toInstant();}
    private static String value(String value){return value==null?"":value;}
    private record QueryRow(long internalId,UUID id,Instant lastRequestedAt){}
    private record SourceRow(long id,UUID publicId){}
    private record ExistingLink(long postingId,long jobId,UUID jobPublicId){}
    private record JobRow(long id,UUID publicId){}
    private record CandidateChoice(DeduplicationPolicy.Candidate candidate,UUID publicId,DeduplicationPolicy.Evaluation evaluation){}
}
