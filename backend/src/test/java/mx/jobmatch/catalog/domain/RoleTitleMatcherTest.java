package mx.jobmatch.catalog.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTitleMatcherTest {
    private static final UUID SOFTWARE = UUID.fromString("11000000-0000-0000-0000-000000000001");
    private static final UUID JAVA = UUID.fromString("11100000-0000-0000-0000-000000000001");
    private static final List<RoleTitleMatcher.Role> ROLES = List.of(
            new RoleTitleMatcher.Role(SOFTWARE, "Desarrollador de software", List.of("software engineer", "desarrollador")),
            new RoleTitleMatcher.Role(JAVA, "Desarrollador Java", List.of("java developer", "desarrollador java")));

    @Test
    void choosesTheMostSpecificAliasAndExtractsSeniority() {
        var match = RoleTitleMatcher.match("Senior Java Developer", ROLES).orElseThrow();
        assertThat(match.roleFamilyId()).isEqualTo(JAVA);
        assertThat(match.seniority()).isEqualTo("SENIOR");
    }

    @Test
    void normalizesAccentsCaseAndPunctuation() {
        var role = new RoleTitleMatcher.Role(JAVA, "Desarrollador Java", List.of("Líder Técnico Java"));
        var match = RoleTitleMatcher.match("LIDER TECNICO JAVA", List.of(role)).orElseThrow();
        assertThat(match.roleFamilyId()).isEqualTo(JAVA);
        assertThat(match.seniority()).isEqualTo("LEAD");
    }

    @Test
    void doesNotMatchFragmentsInsideWords() {
        var role = new RoleTitleMatcher.Role(JAVA, "Desarrollador Java", List.of("go"));
        assertThat(RoleTitleMatcher.match("Golang developer", List.of(role))).isEmpty();
    }
}
