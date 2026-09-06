package mx.jobmatch.profile.adapters.persistence;

import mx.jobmatch.profile.application.ProfileExceptions.InvalidCatalogReference;
import mx.jobmatch.profile.application.ProfileExceptions.InvalidProfile;
import mx.jobmatch.profile.application.ProfileExceptions.VersionConflict;
import mx.jobmatch.profile.application.ProfileRepository;
import mx.jobmatch.profile.domain.ProfessionalProfile;
import mx.jobmatch.profile.domain.ProfileDraft;
import mx.jobmatch.profile.domain.SkillDurationCalculator;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcProfileAdapter implements ProfileRepository {
    private final JdbcClient jdbc;

    public JdbcProfileAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<ProfessionalProfile> findByAccount(UUID accountId) {
        Optional<ProfileRow> row = jdbc.sql("""
                SELECT profile.id internal_id, profile.public_id, profile.version, profile.headline, profile.summary,
                       profile.location, profile.seniority
                FROM profile.professional_profile profile JOIN iam.account account ON account.id=profile.account_id
                WHERE account.public_id=:accountId AND account.status='ACTIVE'
                """).param("accountId", accountId).query((rs, n) -> new ProfileRow(rs.getLong("internal_id"),
                rs.getObject("public_id", UUID.class), rs.getLong("version"), rs.getString("headline"),
                rs.getString("summary"), rs.getString("location"), rs.getString("seniority"))).optional();
        return row.map(this::hydrate);
    }

    @Override
    public ProfessionalProfile replace(UUID accountId, long expectedVersion, ProfileDraft draft,
                                       Map<UUID, SkillDurationCalculator.Projection> projections, int catalogVersion) {
        UUID profileId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO profile.professional_profile(public_id, account_id)
                SELECT :profileId, id FROM iam.account WHERE public_id=:accountId AND status='ACTIVE'
                ON CONFLICT (account_id) DO NOTHING
                """).param("profileId", profileId).param("accountId", accountId).update();
        ProfileRow profile = jdbc.sql("""
                SELECT profile.id internal_id, profile.public_id, profile.version, profile.headline, profile.summary,
                       profile.location, profile.seniority
                FROM profile.professional_profile profile JOIN iam.account account ON account.id=profile.account_id
                WHERE account.public_id=:accountId AND account.status='ACTIVE' FOR UPDATE OF profile
                """).param("accountId", accountId).query((rs, n) -> new ProfileRow(rs.getLong("internal_id"),
                rs.getObject("public_id", UUID.class), rs.getLong("version"), rs.getString("headline"),
                rs.getString("summary"), rs.getString("location"), rs.getString("seniority"))).optional()
                .orElseThrow(() -> new InvalidProfile("La cuenta no está activa."));
        if (profile.version() != expectedVersion) throw new VersionConflict();
        long nextVersion = expectedVersion + 1;

        jdbc.sql("""
                UPDATE profile.professional_profile SET headline=:headline, summary=:summary, location=:location,
                    seniority=:seniority, version=:version, updated_at=now() WHERE id=:profileId
                """).param("headline", draft.headline()).param("summary", draft.summary()).param("location", draft.location())
                .param("seniority", draft.seniority()).param("version", nextVersion).param("profileId", profile.id()).update();
        clearChildren(profile.id());
        insertChildren(profile.id(), nextVersion, draft, projections);
        return findByAccount(accountId).orElseThrow();
    }

    private void clearChildren(long profileId) {
        jdbc.sql("DELETE FROM profile.profile_skill WHERE profile_id=:id").param("id", profileId).update();
        jdbc.sql("DELETE FROM profile.target_role WHERE profile_id=:id").param("id", profileId).update();
        jdbc.sql("DELETE FROM profile.profile_preference WHERE profile_id=:id").param("id", profileId).update();
        jdbc.sql("DELETE FROM profile.excluded_employer WHERE profile_id=:id").param("id", profileId).update();
        jdbc.sql("DELETE FROM profile.trajectory_item WHERE profile_id=:id").param("id", profileId).update();
        jdbc.sql("DELETE FROM profile.education WHERE profile_id=:id").param("id", profileId).update();
        jdbc.sql("DELETE FROM profile.course_certification WHERE profile_id=:id").param("id", profileId).update();
        jdbc.sql("DELETE FROM profile.language WHERE profile_id=:id").param("id", profileId).update();
    }

    private void insertChildren(long profileId, long version, ProfileDraft draft,
                                Map<UUID, SkillDurationCalculator.Projection> projections) {
        for (var role : draft.targetRoles()) {
            int count = jdbc.sql("""
                    INSERT INTO profile.target_role(public_id, profile_id, role_family_id, priority)
                    SELECT :id, :profileId, id, :priority FROM catalog.role_family
                    WHERE public_id=:roleId AND active AND selectable
                    """).param("id", role.id()).param("profileId", profileId).param("priority", role.priority())
                    .param("roleId", role.roleFamilyId()).update();
            if (count != 1) throw new InvalidCatalogReference();
        }
        if (draft.preferences() != null) {
            var preference = draft.preferences();
            jdbc.sql("""
                    INSERT INTO profile.profile_preference(profile_id, remote_mode, employment_type,
                        minimum_monthly_salary, currency, willing_to_relocate)
                    VALUES (:profileId, :remoteMode, :employmentType, :salary, :currency, :relocate)
                    """).param("profileId", profileId).param("remoteMode", preference.remoteMode())
                    .param("employmentType", preference.employmentType()).param("salary", preference.minimumMonthlySalary())
                    .param("currency", preference.currency()).param("relocate", preference.willingToRelocate()).update();
        }
        for (String employer : draft.excludedEmployers()) {
            jdbc.sql("""
                    INSERT INTO profile.excluded_employer(public_id, profile_id, employer_name, employer_name_normalized)
                    VALUES (:id, :profileId, :name, lower(unaccent(:name)))
                    """).param("id", UUID.randomUUID()).param("profileId", profileId).param("name", employer).update();
        }
        Map<UUID, ProfileDraft.TrajectoryItem> trajectory = new HashMap<>();
        for (var item : draft.trajectory()) {
            trajectory.put(item.id(), item);
            jdbc.sql("""
                    INSERT INTO profile.trajectory_item(public_id, profile_id, item_type, title, organization, description,
                        start_year, start_month, end_year, end_month, is_current)
                    VALUES (:id, :profileId, :type, :title, :organization, :description,
                        :startYear, :startMonth, :endYear, :endMonth, :current)
                    """).param("id", item.id()).param("profileId", profileId).param("type", item.type().name())
                    .param("title", item.title()).param("organization", item.organization()).param("description", item.description())
                    .param("startYear", item.startYear()).param("startMonth", item.startMonth())
                    .param("endYear", item.endYear()).param("endMonth", item.endMonth()).param("current", item.current()).update();
        }
        for (var item : draft.education()) {
            jdbc.sql("""
                    INSERT INTO profile.education(public_id, profile_id, institution, degree, field_of_study, start_year, end_year)
                    VALUES (:id, :profileId, :institution, :degree, :field, :startYear, :endYear)
                    """).param("id", item.id()).param("profileId", profileId).param("institution", item.institution())
                    .param("degree", item.degree()).param("field", item.fieldOfStudy()).param("startYear", item.startYear())
                    .param("endYear", item.endYear()).update();
        }
        for (var item : draft.certifications()) {
            jdbc.sql("""
                    INSERT INTO profile.course_certification(public_id, profile_id, name, issuer, issued_year, credential_id, credential_url)
                    VALUES (:id, :profileId, :name, :issuer, :year, :credentialId, :credentialUrl)
                    """).param("id", item.id()).param("profileId", profileId).param("name", item.name())
                    .param("issuer", item.issuer()).param("year", item.issuedYear()).param("credentialId", item.credentialId())
                    .param("credentialUrl", item.credentialUrl()).update();
        }
        for (var item : draft.languages()) {
            jdbc.sql("""
                    INSERT INTO profile.language(public_id, profile_id, language_code, display_name, proficiency)
                    VALUES (:id, :profileId, :code, :name, :proficiency)
                    """).param("id", item.id()).param("profileId", profileId).param("code", item.code())
                    .param("name", item.name()).param("proficiency", item.proficiency()).update();
        }
        for (var skill : draft.skills()) insertSkill(profileId, version, skill, projections.get(skill.id()), trajectory);
    }

    private void insertSkill(long profileId, long version, ProfileDraft.Skill skill,
                             SkillDurationCalculator.Projection projection,
                             Map<UUID, ProfileDraft.TrajectoryItem> trajectory) {
        Long skillId;
        if (skill.catalogSkillId() != null) {
            skillId = jdbc.sql("SELECT id FROM catalog.skill WHERE public_id=:id AND active")
                    .param("id", skill.catalogSkillId()).query(Long.class).optional().orElseThrow(InvalidCatalogReference::new);
        } else skillId = null;
        long profileSkillId = jdbc.sql("""
                INSERT INTO profile.profile_skill(public_id, profile_id, skill_id, custom_name, match_eligible, proficiency)
                VALUES (:id, :profileId, :skillId, :customName, :eligible, :proficiency) RETURNING id
                """).param("id", skill.id()).param("profileId", profileId).param("skillId", skillId)
                .param("customName", skill.customName()).param("eligible", skill.matchEligible())
                .param("proficiency", skill.proficiency()).query(Long.class).single();
        for (UUID trajectoryId : skill.evidenceTrajectoryIds()) {
            var item = trajectory.get(trajectoryId);
            int count = jdbc.sql("""
                    INSERT INTO profile.skill_evidence(public_id, profile_skill_id, trajectory_item_id, context_type,
                        context_weight, source, profile_version)
                    SELECT :id, :profileSkillId, id, :context, :weight, 'MANUAL', :version
                    FROM profile.trajectory_item WHERE public_id=:trajectoryId AND profile_id=:profileId
                    """).param("id", UUID.randomUUID()).param("profileSkillId", profileSkillId)
                    .param("context", item.type().name()).param("weight", item.type().weight())
                    .param("version", version).param("trajectoryId", trajectoryId).param("profileId", profileId).update();
            if (count != 1) throw new InvalidProfile("Evidencia fuera del perfil.");
        }
        jdbc.sql("""
                INSERT INTO profile.skill_duration_projection(profile_skill_id, professional_months,
                    weighted_practical_months, profile_version) VALUES (:id, :professional, :practical, :version)
                """).param("id", profileSkillId).param("professional", projection.professionalMonths())
                .param("practical", projection.weightedPracticalMonths()).param("version", version).update();
    }

    private ProfessionalProfile hydrate(ProfileRow row) {
        long id = row.id();
        int catalogVersion = jdbc.sql("SELECT max(version_no) FROM catalog.catalog_version WHERE status='PUBLISHED'")
                .query(Integer.class).single();
        var roles = jdbc.sql("""
                SELECT target.public_id, family.public_id role_id, target.priority, family.display_name
                FROM profile.target_role target JOIN catalog.role_family family ON family.id=target.role_family_id
                WHERE target.profile_id=:id ORDER BY target.priority
                """).param("id", id).query((rs, n) -> new ProfileDraft.TargetRole(rs.getObject("public_id", UUID.class),
                rs.getObject("role_id", UUID.class), rs.getInt("priority"), rs.getString("display_name"))).list();
        var preference = jdbc.sql("""
                SELECT remote_mode, employment_type, minimum_monthly_salary, currency, willing_to_relocate
                FROM profile.profile_preference WHERE profile_id=:id
                """).param("id", id).query((rs, n) -> new ProfileDraft.Preference(rs.getString("remote_mode"),
                rs.getString("employment_type"), rs.getBigDecimal("minimum_monthly_salary"), rs.getString("currency").strip(),
                rs.getBoolean("willing_to_relocate"))).optional().orElse(null);
        var employers = jdbc.sql("SELECT employer_name FROM profile.excluded_employer WHERE profile_id=:id ORDER BY employer_name")
                .param("id", id).query(String.class).list();
        var trajectory = jdbc.sql("""
                SELECT public_id, item_type, title, organization, description, start_year, start_month,
                       end_year, end_month, is_current FROM profile.trajectory_item
                WHERE profile_id=:id ORDER BY start_year DESC, start_month DESC, public_id
                """).param("id", id).query((rs, n) -> new ProfileDraft.TrajectoryItem(rs.getObject("public_id", UUID.class),
                mx.jobmatch.profile.domain.TrajectoryType.valueOf(rs.getString("item_type")), rs.getString("title"),
                rs.getString("organization"), rs.getString("description"), rs.getInt("start_year"), rs.getInt("start_month"),
                integer(rs, "end_year"), integer(rs, "end_month"), rs.getBoolean("is_current"))).list();
        var education = jdbc.sql("""
                SELECT public_id, institution, degree, field_of_study, start_year, end_year FROM profile.education
                WHERE profile_id=:id ORDER BY end_year DESC NULLS FIRST, public_id
                """).param("id", id).query((rs, n) -> new ProfileDraft.Education(rs.getObject("public_id", UUID.class),
                rs.getString("institution"), rs.getString("degree"), rs.getString("field_of_study"),
                integer(rs, "start_year"), integer(rs, "end_year"))).list();
        var certifications = jdbc.sql("""
                SELECT public_id, name, issuer, issued_year, credential_id, credential_url FROM profile.course_certification
                WHERE profile_id=:id ORDER BY issued_year DESC NULLS LAST, public_id
                """).param("id", id).query((rs, n) -> new ProfileDraft.Certification(rs.getObject("public_id", UUID.class),
                rs.getString("name"), rs.getString("issuer"), integer(rs, "issued_year"), rs.getString("credential_id"),
                rs.getString("credential_url"))).list();
        var languages = jdbc.sql("""
                SELECT public_id, language_code, display_name, proficiency FROM profile.language
                WHERE profile_id=:id ORDER BY display_name
                """).param("id", id).query((rs, n) -> new ProfileDraft.Language(rs.getObject("public_id", UUID.class),
                rs.getString("language_code"), rs.getString("display_name"), rs.getString("proficiency"))).list();
        var skills = loadSkills(id);
        return new ProfessionalProfile(row.publicId(), row.version(), catalogVersion,
                new ProfileDraft(row.headline(), row.summary(), row.location(), row.seniority(), roles, preference,
                        employers, trajectory, education, certifications, languages, skills));
    }

    private List<ProfileDraft.Skill> loadSkills(long profileId) {
        var rows = jdbc.sql("""
                SELECT ps.id internal_id, ps.public_id, skill.public_id catalog_skill_id, ps.custom_name,
                       COALESCE(skill.display_name, ps.custom_name) display_name, ps.proficiency, ps.match_eligible,
                       projection.professional_months, projection.weighted_practical_months
                FROM profile.profile_skill ps LEFT JOIN catalog.skill skill ON skill.id=ps.skill_id
                JOIN profile.skill_duration_projection projection ON projection.profile_skill_id=ps.id
                WHERE ps.profile_id=:id ORDER BY display_name
                """).param("id", profileId).query((rs, n) -> new SkillRow(rs.getLong("internal_id"),
                rs.getObject("public_id", UUID.class), rs.getObject("catalog_skill_id", UUID.class),
                rs.getString("custom_name"), rs.getString("display_name"), rs.getString("proficiency"),
                rs.getBoolean("match_eligible"), rs.getInt("professional_months"),
                rs.getBigDecimal("weighted_practical_months"))).list();
        List<ProfileDraft.Skill> result = new ArrayList<>();
        for (var row : rows) {
            var evidence = jdbc.sql("""
                    SELECT trajectory.public_id FROM profile.skill_evidence evidence
                    JOIN profile.trajectory_item trajectory ON trajectory.id=evidence.trajectory_item_id
                    WHERE evidence.profile_skill_id=:id ORDER BY trajectory.public_id
                    """).param("id", row.id()).query(UUID.class).list();
            result.add(new ProfileDraft.Skill(row.publicId(), row.catalogSkillId(), row.customName(), row.name(),
                    row.proficiency(), row.matchEligible(), evidence, row.professionalMonths(), row.practicalMonths()));
        }
        return result;
    }

    private static Integer integer(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private record ProfileRow(long id, UUID publicId, long version, String headline, String summary,
                              String location, String seniority) {}
    private record SkillRow(long id, UUID publicId, UUID catalogSkillId, String customName, String name,
                            String proficiency, boolean matchEligible, int professionalMonths, BigDecimal practicalMonths) {}
}
