package mx.jobmatch.profile.application;

import mx.jobmatch.catalog.application.CatalogQueryPort;
import mx.jobmatch.operations.application.OutboxPort;
import mx.jobmatch.operations.application.BackgroundTaskPort;
import mx.jobmatch.profile.domain.ProfessionalProfile;
import mx.jobmatch.profile.domain.ProfileDraft;
import mx.jobmatch.profile.domain.SkillDurationCalculator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.YearMonth;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static mx.jobmatch.profile.application.ProfileExceptions.InvalidProfile;

@Profile("api")
@Service
public class ProfileService {
    private static final Set<String> SENIORITIES = Set.of("INTERN", "JUNIOR", "MID", "SENIOR", "LEAD", "MANAGER", "DIRECTOR");
    private static final Set<String> REMOTE_MODES = Set.of("REMOTE", "HYBRID", "ONSITE", "ANY");
    private static final Set<String> EMPLOYMENT_TYPES = Set.of("FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP", "ANY");
    private static final Set<String> LANGUAGE_LEVELS = Set.of("BASIC", "CONVERSATIONAL", "PROFESSIONAL", "FLUENT", "NATIVE");
    private static final Set<String> SKILL_LEVELS = Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT");
    private final ProfileRepository profiles;
    private final CatalogQueryPort catalog;
    private final OutboxPort outbox;
    private final BackgroundTaskPort tasks;

    public ProfileService(ProfileRepository profiles, CatalogQueryPort catalog, OutboxPort outbox,
                          BackgroundTaskPort tasks) {
        this.profiles = profiles;
        this.catalog = catalog;
        this.outbox = outbox;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public ProfessionalProfile get(UUID accountId) {
        return profiles.findByAccount(accountId).orElseGet(() -> new ProfessionalProfile(
                null, 0, catalog.currentVersion(), new ProfileDraft(null, null, null, null,
                List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of())));
    }

    @Transactional
    public ProfessionalProfile replace(UUID accountId, long expectedVersion, ProfileDraft input) {
        ProfileDraft draft = normalizeAndValidate(input);
        Map<UUID, SkillDurationCalculator.Projection> projections = calculateProjections(draft);
        int catalogVersion = catalog.currentVersion();
        ProfessionalProfile saved = profiles.replace(accountId, expectedVersion, draft, projections, catalogVersion);
        outbox.append("PROFILE", saved.id(), "ProfileChanged",
                "{\"profileId\":\"" + saved.id() + "\",\"profileVersion\":" + saved.version()
                        + ",\"catalogVersion\":" + catalogVersion + "}");
        tasks.enqueue("REFRESH_RECOMMENDATIONS", "{\"accountId\":\"" + accountId + "\"}",
                "recommendations:" + saved.id() + ":" + saved.version() + ":" + catalogVersion,
                Instant.now(), 5);
        return saved;
    }

    private ProfileDraft normalizeAndValidate(ProfileDraft input) {
        if (input == null) throw new InvalidProfile("El perfil es obligatorio.");
        requireMax(input.targetRoles(), 10, "roles objetivo");
        requireMax(input.excludedEmployers(), 50, "empresas excluidas");
        requireMax(input.trajectory(), 100, "elementos de trayectoria");
        requireMax(input.education(), 30, "elementos de educación");
        requireMax(input.certifications(), 50, "certificaciones");
        requireMax(input.languages(), 20, "idiomas");
        requireMax(input.skills(), 100, "habilidades");
        String seniority = choice(input.seniority(), SENIORITIES, "seniority");

        Set<UUID> roleIds = new HashSet<>();
        Set<Integer> priorities = new HashSet<>();
        var roles = input.targetRoles().stream().map(role -> {
            if (role.roleFamilyId() == null || role.priority() < 1 || role.priority() > 10
                    || !roleIds.add(role.roleFamilyId()) || !priorities.add(role.priority())) {
                throw new InvalidProfile("Los roles objetivo y sus prioridades deben ser únicos.");
            }
            return new ProfileDraft.TargetRole(id(role.id()), role.roleFamilyId(), role.priority(), null);
        }).toList();

        var trajectory = input.trajectory().stream().map(item -> {
            if (item.type() == null || blank(item.title())) throw new InvalidProfile("Cada trayectoria requiere tipo y título.");
            year(item.startYear());
            year(item.endYear());
            if ((item.current() && (item.endYear() != null || item.endMonth() != null))
                    || (!item.current() && (item.endYear() == null || item.endMonth() == null)))
                throw new InvalidProfile("Las fechas de trayectoria no son válidas.");
            var normalized = new ProfileDraft.TrajectoryItem(id(item.id()), item.type(), text(item.title(), 180),
                    text(item.organization(), 180), text(item.description(), 3000), item.startYear(), item.startMonth(),
                    item.endYear(), item.endMonth(), item.current());
            try { SkillDurationCalculator.calculate(List.of(normalized), YearMonth.now()); }
            catch (RuntimeException invalid) { throw new InvalidProfile("Las fechas de trayectoria no son válidas."); }
            return normalized;
        }).toList();
        Set<UUID> trajectoryIds = new HashSet<>();
        trajectory.forEach(item -> { if (!trajectoryIds.add(item.id())) throw new InvalidProfile("UUID de trayectoria repetido."); });

        var education = input.education().stream().map(item -> {
            if (blank(item.institution()) || blank(item.degree())) throw new InvalidProfile("La educación requiere institución y grado.");
            if (item.startYear() != null && item.endYear() != null && item.endYear() < item.startYear())
                throw new InvalidProfile("Las fechas de educación no son válidas.");
            year(item.startYear());
            year(item.endYear());
            return new ProfileDraft.Education(id(item.id()), text(item.institution(), 200), text(item.degree(), 200),
                    text(item.fieldOfStudy(), 200), item.startYear(), item.endYear());
        }).toList();
        var certifications = input.certifications().stream().map(item -> {
            if (blank(item.name())) throw new InvalidProfile("Cada certificación requiere nombre.");
            year(item.issuedYear());
            return new ProfileDraft.Certification(id(item.id()), text(item.name(), 220), text(item.issuer(), 200),
                    item.issuedYear(), text(item.credentialId(), 200), text(item.credentialUrl(), 1000));
        }).toList();
        Set<String> languageCodes = new HashSet<>();
        var languages = input.languages().stream().map(item -> {
            String rawCode = text(item.code(), 10);
            if (blank(rawCode)) throw new InvalidProfile("Cada idioma requiere código.");
            String code = rawCode.toLowerCase(java.util.Locale.ROOT);
            String proficiency = choice(item.proficiency(), LANGUAGE_LEVELS, "nivel de idioma");
            if (blank(item.name()) || blank(proficiency) || !languageCodes.add(code))
                throw new InvalidProfile("Los idiomas requieren código único, nombre y nivel.");
            return new ProfileDraft.Language(id(item.id()), code, text(item.name(), 100), proficiency);
        }).toList();

        Set<String> skillKeys = new HashSet<>();
        var skills = input.skills().stream().map(skill -> {
            boolean normalized = skill.catalogSkillId() != null;
            boolean custom = !blank(skill.customName());
            if (normalized == custom) throw new InvalidProfile("Una habilidad debe ser normalizada o personalizada, nunca ambas.");
            String key = normalized ? "catalog:" + skill.catalogSkillId() : "custom:" + text(skill.customName(), 150).toLowerCase(java.util.Locale.ROOT);
            if (!skillKeys.add(key)) throw new InvalidProfile("Habilidad repetida.");
            if (!trajectoryIds.containsAll(skill.evidenceTrajectoryIds()))
                throw new InvalidProfile("Una evidencia no pertenece a la trayectoria enviada.");
            return new ProfileDraft.Skill(id(skill.id()), skill.catalogSkillId(), custom ? text(skill.customName(), 150) : null,
                    null, choice(skill.proficiency(), SKILL_LEVELS, "nivel de habilidad"), normalized,
                    skill.evidenceTrajectoryIds(), 0, BigDecimal.ZERO);
        }).toList();

        var preference = input.preferences() == null ? null : new ProfileDraft.Preference(
                choice(input.preferences().remoteMode(), REMOTE_MODES, "modalidad"),
                choice(input.preferences().employmentType(), EMPLOYMENT_TYPES, "tipo de empleo"),
                input.preferences().minimumMonthlySalary(),
                blank(input.preferences().currency()) ? "MXN" : text(input.preferences().currency(), 3).toUpperCase(java.util.Locale.ROOT),
                input.preferences().willingToRelocate());
        if (preference != null && preference.minimumMonthlySalary() != null
                && preference.minimumMonthlySalary().signum() < 0) throw new InvalidProfile("El salario mínimo no puede ser negativo.");
        var employers = normalizedEmployers(input.excludedEmployers());

        uniqueIds(roles.stream().map(ProfileDraft.TargetRole::id).toList(), "rol objetivo");
        uniqueIds(education.stream().map(ProfileDraft.Education::id).toList(), "educación");
        uniqueIds(certifications.stream().map(ProfileDraft.Certification::id).toList(), "certificación");
        uniqueIds(languages.stream().map(ProfileDraft.Language::id).toList(), "idioma");
        uniqueIds(skills.stream().map(ProfileDraft.Skill::id).toList(), "habilidad");

        return new ProfileDraft(text(input.headline(), 160), text(input.summary(), 3000), text(input.location(), 160),
                seniority, roles, preference, employers, trajectory, education,
                certifications, languages, skills);
    }

    private Map<UUID, SkillDurationCalculator.Projection> calculateProjections(ProfileDraft draft) {
        Map<UUID, ProfileDraft.TrajectoryItem> trajectory = new HashMap<>();
        draft.trajectory().forEach(item -> trajectory.put(item.id(), item));
        Map<UUID, SkillDurationCalculator.Projection> result = new HashMap<>();
        for (var skill : draft.skills()) {
            var evidence = skill.evidenceTrajectoryIds().stream().map(trajectory::get).toList();
            result.put(skill.id(), SkillDurationCalculator.calculate(evidence, YearMonth.now()));
        }
        return result;
    }

    private static UUID id(UUID value) { return value == null ? UUID.randomUUID() : value; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String text(String value, int max) {
        if (value == null) return null;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
        if (normalized.codePointCount(0, normalized.length()) > max) throw new InvalidProfile("Un campo excede la longitud permitida.");
        return normalized.isEmpty() ? null : normalized;
    }
    private static void requireMax(List<?> values, int max, String label) {
        if (values.size() > max) throw new InvalidProfile("Demasiados " + label + ".");
    }
    private static String choice(String value, Set<String> choices, String label) {
        String normalized = text(value, 30);
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(java.util.Locale.ROOT);
        if (!choices.contains(normalized)) throw new InvalidProfile("Valor de " + label + " no válido.");
        return normalized;
    }
    private static void year(Integer year) {
        if (year != null && (year < 1950 || year > 2200)) throw new InvalidProfile("El año no es válido.");
    }
    private static void year(int year) { year(Integer.valueOf(year)); }
    private static void uniqueIds(List<UUID> ids, String label) {
        if (new HashSet<>(ids).size() != ids.size()) throw new InvalidProfile("UUID de " + label + " repetido.");
    }
    private static List<String> normalizedEmployers(List<String> names) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String value : names) {
            String name = text(value, 200);
            if (name != null) {
                String key = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                        .toLowerCase(java.util.Locale.ROOT);
                unique.putIfAbsent(key, name);
            }
        }
        return List.copyOf(unique.values());
    }
}
