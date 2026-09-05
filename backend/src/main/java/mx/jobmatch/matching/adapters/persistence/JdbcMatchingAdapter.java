package mx.jobmatch.matching.adapters.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.jobmatch.matching.application.MatchingRepository;
import mx.jobmatch.matching.domain.JobFacts;
import mx.jobmatch.matching.domain.MatchEvaluation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcMatchingAdapter implements MatchingRepository {
    private final JdbcClient jdbc;private final ObjectMapper json;
    public JdbcMatchingAdapter(JdbcClient jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

    @Override public List<CatalogSkill> catalogSkills(){
        var rows=jdbc.sql("""
                SELECT skill.public_id,skill.display_name,alias.alias FROM catalog.skill skill
                LEFT JOIN catalog.skill_alias alias ON alias.skill_id=skill.id WHERE skill.active
                ORDER BY skill.id,alias.id
                """).query((rs,row)->new SkillRow(rs.getObject("public_id",UUID.class),rs.getString("display_name"),rs.getString("alias"))).list();
        Map<UUID,MutableSkill> result=new LinkedHashMap<>();
        for(var row:rows){var current=result.computeIfAbsent(row.id(),id->new MutableSkill(id,row.name()));
            if(row.alias()!=null&&!current.aliases.contains(row.alias()))current.aliases.add(row.alias());}
        return result.values().stream().map(skill->new CatalogSkill(skill.id,skill.name,List.copyOf(skill.aliases))).toList();
    }

    @Override public Optional<JobFacts> findFacts(UUID jobId,long jobVersion,int catalogVersion,String extractorVersion){
        return jdbc.sql("""
                SELECT snapshot.facts::text FROM matching.job_fact_snapshot snapshot JOIN jobs.canonical_job job ON job.id=snapshot.canonical_job_id
                WHERE job.public_id=:job AND snapshot.job_version=:jobVersion AND snapshot.catalog_version=:catalog
                  AND snapshot.extractor_version=:extractor
                """).param("job",jobId).param("jobVersion",jobVersion).param("catalog",catalogVersion).param("extractor",extractorVersion)
                .query(String.class).optional().map(value->read(value,JobFacts.class));
    }

    @Override public void saveFacts(UUID jobId,long jobVersion,int catalogVersion,String extractorVersion,JobFacts facts){
        jdbc.sql("""
                INSERT INTO matching.job_fact_snapshot(public_id,canonical_job_id,job_version,catalog_version,extractor_version,facts)
                SELECT :id,job.id,:jobVersion,:catalog,:extractor,CAST(:facts AS jsonb) FROM jobs.canonical_job job WHERE job.public_id=:job
                ON CONFLICT(canonical_job_id,job_version,catalog_version,extractor_version) DO NOTHING
                """).param("id",UUID.randomUUID()).param("jobVersion",jobVersion).param("catalog",catalogVersion)
                .param("extractor",extractorVersion).param("facts",write(facts)).param("job",jobId).update();
    }

    @Override public Optional<MatchEvaluation> find(UUID profileId,UUID jobId,long profileVersion,long jobVersion,
                                                     int catalogVersion,String scoringVersion){
        return jdbc.sql("""
                SELECT result.id internal_id,result.public_id,result.score,result.classification,result.component_scores::text
                FROM matching.match_result result JOIN profile.professional_profile profile ON profile.id=result.profile_id
                JOIN jobs.canonical_job job ON job.id=result.canonical_job_id
                WHERE profile.public_id=:profile AND job.public_id=:job AND result.profile_version=:profileVersion
                  AND result.job_version=:jobVersion AND result.catalog_version=:catalog AND result.scoring_version=:scoring
                """).param("profile",profileId).param("job",jobId).param("profileVersion",profileVersion).param("jobVersion",jobVersion)
                .param("catalog",catalogVersion).param("scoring",scoringVersion).query((rs,row)->new ResultRow(rs.getLong("internal_id"),
                        rs.getObject("public_id",UUID.class),rs.getBigDecimal("score"),rs.getString("classification"),rs.getString("component_scores")))
                .optional().map(row->hydrate(row,jobId,profileVersion,jobVersion,catalogVersion,scoringVersion));
    }

    @Override public Optional<MatchEvaluation> findById(UUID accountId,UUID resultId){
        return jdbc.sql("""
                SELECT result.id internal_id,result.public_id,result.score,result.classification,result.component_scores::text,
                  job.public_id job_id,result.profile_version,result.job_version,result.catalog_version,result.scoring_version
                FROM matching.match_result result JOIN profile.professional_profile profile ON profile.id=result.profile_id
                JOIN iam.account account ON account.id=profile.account_id JOIN jobs.canonical_job job ON job.id=result.canonical_job_id
                WHERE account.public_id=:account AND result.public_id=:result
                """).param("account",accountId).param("result",resultId).query((rs,row)->new OwnedResult(resultRow(rs,row),
                rs.getObject("job_id",UUID.class),rs.getLong("profile_version"),rs.getLong("job_version"),
                rs.getInt("catalog_version"),rs.getString("scoring_version"))).optional().map(result->hydrate(result.row(),result.jobId(),
                result.profileVersion(),result.jobVersion(),result.catalogVersion(),result.scoringVersion()));
    }

    @Override @Transactional public MatchEvaluation save(UUID profileId,MatchEvaluation evaluation){
        Optional<ResultRow> inserted=jdbc.sql("""
                INSERT INTO matching.match_result(public_id,profile_id,canonical_job_id,profile_version,job_version,
                  catalog_version,scoring_version,score,classification,component_scores)
                SELECT :id,profile.id,job.id,:profileVersion,:jobVersion,:catalog,:scoring,:score,:classification,CAST(:components AS jsonb)
                FROM profile.professional_profile profile,jobs.canonical_job job WHERE profile.public_id=:profile AND job.public_id=:job
                ON CONFLICT(profile_id,canonical_job_id,profile_version,job_version,catalog_version,scoring_version) DO NOTHING
                RETURNING id internal_id,public_id,score,classification,component_scores::text
                """).param("id",evaluation.id()).param("profileVersion",evaluation.profileVersion()).param("jobVersion",evaluation.jobVersion())
                .param("catalog",evaluation.catalogVersion()).param("scoring",evaluation.scoringVersion()).param("score",evaluation.score())
                .param("classification",evaluation.classification()).param("components",write(evaluation.components()))
                .param("profile",profileId).param("job",evaluation.jobId()).query(this::resultRow).optional();
        if(inserted.isEmpty())return find(profileId,evaluation.jobId(),evaluation.profileVersion(),evaluation.jobVersion(),
                evaluation.catalogVersion(),evaluation.scoringVersion()).orElseThrow();
        int position=0;
        for(var reason:evaluation.reasons())jdbc.sql("""
                INSERT INTO matching.match_reason(public_id,match_result_id,component,reason_type,requirement_public_id,
                  requirement_snapshot,evidence_snapshot,points,explanation,position)
                VALUES (:id,:result,:component,:type,:requirementId,CAST(:requirement AS jsonb),CAST(:evidence AS jsonb),:points,:explanation,:position)
                """).param("id",reason.id()).param("result",inserted.get().id()).param("component",reason.component()).param("type",reason.type())
                .param("requirementId",reason.requirementId()).param("requirement",write(reason.requirement())).param("evidence",write(reason.evidence()))
                .param("points",reason.points()).param("explanation",reason.explanation()).param("position",position++).update();
        return evaluation;
    }

    @Override public List<UUID> recommendationCandidates(UUID accountId,int maximum){
        return jdbc.sql("""
                SELECT DISTINCT job.public_id,MIN(target.priority) priority,job.published_at
                FROM iam.account account JOIN profile.professional_profile profile ON profile.account_id=account.id
                JOIN profile.target_role target ON target.profile_id=profile.id
                JOIN jobs.canonical_job job ON job.role_family_id=target.role_family_id
                JOIN jobs.employer employer ON employer.id=job.employer_id
                LEFT JOIN profile.profile_preference preference ON preference.profile_id=profile.id
                WHERE account.public_id=:account AND job.status='ACTIVE'
                  AND NOT EXISTS(SELECT 1 FROM profile.excluded_employer excluded WHERE excluded.profile_id=profile.id
                    AND excluded.employer_name_normalized=employer.name_normalized)
                  AND (preference.remote_mode IS NULL OR preference.remote_mode='ANY' OR preference.remote_mode=job.remote_mode)
                  AND (preference.employment_type IS NULL OR preference.employment_type='ANY' OR preference.employment_type=job.employment_type)
                  AND (preference.minimum_monthly_salary IS NULL OR
                    (job.salary_max_monthly IS NOT NULL AND job.salary_max_monthly>=preference.minimum_monthly_salary))
                GROUP BY job.public_id,job.published_at ORDER BY priority,job.published_at DESC,job.public_id DESC LIMIT :maximum
                """).param("account",accountId).param("maximum",maximum).query((rs,row)->rs.getObject("public_id",UUID.class)).list();
    }

    @Override public List<UUID> pendingProfileAccounts(int maximum){
        return jdbc.sql("""
                SELECT account.public_id FROM profile.professional_profile profile JOIN iam.account account ON account.id=profile.account_id
                LEFT JOIN matching.recommendation_generation generation ON generation.profile_id=profile.id
                WHERE account.status='ACTIVE' AND EXISTS(SELECT 1 FROM profile.target_role WHERE profile_id=profile.id)
                  AND (generation.profile_id IS NULL OR generation.profile_version<>profile.version
                    OR generation.catalog_version<>(SELECT max(version_no) FROM catalog.catalog_version WHERE status='PUBLISHED')
                    OR EXISTS(SELECT 1 FROM profile.target_role target JOIN jobs.canonical_job job ON job.role_family_id=target.role_family_id
                      WHERE target.profile_id=profile.id AND job.updated_at>generation.generated_at))
                ORDER BY profile.updated_at,profile.id LIMIT :maximum
                """).param("maximum",maximum).query(UUID.class).list();
    }

    @Override @Transactional public void replaceRecommendations(UUID profileId,long profileVersion,List<UUID> resultIds){
        long profile=jdbc.sql("SELECT id FROM profile.professional_profile WHERE public_id=:id").param("id",profileId).query(Long.class).single();
        jdbc.sql("DELETE FROM matching.recommendation WHERE profile_id=:profile AND profile_version=:version")
                .param("profile",profile).param("version",profileVersion).update();
        int rank=1;for(UUID id:resultIds.stream().limit(500).toList())jdbc.sql("""
                INSERT INTO matching.recommendation(profile_id,profile_version,match_result_id,rank)
                SELECT :profile,:version,id,:rank FROM matching.match_result WHERE public_id=:result
                """).param("profile",profile).param("version",profileVersion).param("rank",rank++).param("result",id).update();
        jdbc.sql("""
                INSERT INTO matching.recommendation_generation(profile_id,profile_version,catalog_version,generated_at)
                VALUES (:profile,:version,(SELECT max(version_no) FROM catalog.catalog_version WHERE status='PUBLISHED'),now())
                ON CONFLICT(profile_id) DO UPDATE SET profile_version=excluded.profile_version,
                  catalog_version=excluded.catalog_version,generated_at=excluded.generated_at
                """).param("profile",profile).param("version",profileVersion).update();
    }

    @Override @Transactional public int deleteObsolete(){
        int removed=jdbc.sql("""
                DELETE FROM matching.match_result result USING profile.professional_profile profile,jobs.canonical_job job
                WHERE result.profile_id=profile.id AND result.canonical_job_id=job.id
                  AND (result.profile_version<>profile.version OR result.job_version<>job.version
                    OR result.catalog_version<>(SELECT max(version_no) FROM catalog.catalog_version WHERE status='PUBLISHED')
                    OR result.scoring_version<>:scoring)
                """).param("scoring",mx.jobmatch.matching.application.DeterministicScoringV1.VERSION).update();
        jdbc.sql("""
                DELETE FROM matching.job_fact_snapshot snapshot USING jobs.canonical_job job
                WHERE snapshot.canonical_job_id=job.id AND (snapshot.job_version<>job.version
                  OR snapshot.catalog_version<>(SELECT max(version_no) FROM catalog.catalog_version WHERE status='PUBLISHED')
                  OR snapshot.extractor_version<>:extractor)
                """).param("extractor",mx.jobmatch.matching.application.JobFactExtractor.VERSION).update();
        return removed;
    }

    private MatchEvaluation hydrate(ResultRow row,UUID jobId,long profileVersion,long jobVersion,int catalogVersion,String scoring){
        var reasons=jdbc.sql("""
                SELECT public_id,component,reason_type,requirement_public_id,requirement_snapshot::text,evidence_snapshot::text,points,explanation
                FROM matching.match_reason WHERE match_result_id=:id ORDER BY position
                """).param("id",row.id()).query((rs,index)->new MatchEvaluation.Reason(rs.getObject("public_id",UUID.class),rs.getString("component"),
                rs.getString("reason_type"),rs.getObject("requirement_public_id",UUID.class),readMap(rs.getString("requirement_snapshot")),
                readMap(rs.getString("evidence_snapshot")),rs.getBigDecimal("points"),rs.getString("explanation"))).list();
        Map<String,BigDecimal> components=read(row.components(),new TypeReference<>(){});
        return new MatchEvaluation(row.publicId(),jobId,profileVersion,jobVersion,catalogVersion,scoring,row.score(),row.classification(),components,reasons,true);
    }
    private ResultRow resultRow(java.sql.ResultSet rs,int row)throws java.sql.SQLException{return new ResultRow(rs.getLong("internal_id"),
            rs.getObject("public_id",UUID.class),rs.getBigDecimal("score"),rs.getString("classification"),rs.getString("component_scores"));}
    private Map<String,Object> readMap(String value){return read(value,new TypeReference<>(){});}
    private <T>T read(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception failure){throw new IllegalStateException(failure);}}
    private <T>T read(String value,TypeReference<T> type){try{return json.readValue(value,type);}catch(Exception failure){throw new IllegalStateException(failure);}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception failure){throw new IllegalStateException(failure);}}
    private record SkillRow(UUID id,String name,String alias){}
    private static final class MutableSkill {
        private final UUID id;private final String name;private final List<String> aliases=new ArrayList<>();
        private MutableSkill(UUID id,String name){this.id=id;this.name=name;this.aliases.add(name);}
    }
    private record ResultRow(long id,UUID publicId,BigDecimal score,String classification,String components){}
    private record OwnedResult(ResultRow row,UUID jobId,long profileVersion,long jobVersion,int catalogVersion,String scoringVersion){}
}
