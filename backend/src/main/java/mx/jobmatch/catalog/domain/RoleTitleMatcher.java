package mx.jobmatch.catalog.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class RoleTitleMatcher {
    private RoleTitleMatcher() {}

    public static Optional<Match> match(String title, List<Role> roles) {
        String normalizedTitle = normalize(title);
        if (normalizedTitle.isEmpty()) return Optional.empty();

        List<Candidate> candidates = new ArrayList<>();
        for (Role role : roles) {
            add(candidates, normalizedTitle, role, role.displayName());
            role.aliases().forEach(alias -> add(candidates, normalizedTitle, role, alias));
        }
        return candidates.stream()
                .sorted(Comparator.comparing(Candidate::exact).reversed()
                        .thenComparing(Comparator.comparingInt(
                                (Candidate candidate) -> candidate.normalizedAlias().length()).reversed())
                        .thenComparing(candidate -> candidate.role().displayName())
                        .thenComparing(candidate -> candidate.role().id()))
                .findFirst()
                .map(candidate -> new Match(candidate.role().id(), seniority(normalizedTitle)));
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9+#.]+", " ")
                .strip()
                .replaceAll("\\s+", " ");
    }

    public static String seniority(String normalizedTitle) {
        if (hasAny(normalizedTitle, "director", "head of")) return "DIRECTOR";
        if (hasAny(normalizedTitle, "engineering manager", "manager", "gerente")) return "MANAGER";
        if (hasAny(normalizedTitle, "tech lead", "technical lead", "lider tecnico", "lead")) return "LEAD";
        if (hasAny(normalizedTitle, "semi senior", "semisenior", "ssr", "mid", "middle")) return "MID";
        if (hasAny(normalizedTitle, "senior", "sr")) return "SENIOR";
        if (hasAny(normalizedTitle, "junior", "jr")) return "JUNIOR";
        if (hasAny(normalizedTitle, "intern", "internship", "practicante", "becario")) return "INTERN";
        return null;
    }

    private static void add(List<Candidate> candidates, String title, Role role, String alias) {
        String normalizedAlias = normalize(alias);
        if (!normalizedAlias.isEmpty() && phrase(title, normalizedAlias)) {
            candidates.add(new Candidate(role, normalizedAlias, title.equals(normalizedAlias)));
        }
    }

    private static boolean phrase(String text, String phrase) {
        return (" " + text + " ").contains(" " + phrase + " ");
    }

    private static boolean hasAny(String title, String... terms) {
        for (String term : terms) if (phrase(title, term)) return true;
        return false;
    }

    public record Role(UUID id, String displayName, List<String> aliases) {
        public Role {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }

    public record Match(UUID roleFamilyId, String seniority) {}
    private record Candidate(Role role, String normalizedAlias, boolean exact) {}
}
