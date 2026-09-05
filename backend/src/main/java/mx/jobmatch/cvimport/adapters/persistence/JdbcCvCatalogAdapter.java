package mx.jobmatch.cvimport.adapters.persistence;

import mx.jobmatch.cvimport.application.CvCatalogPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcCvCatalogAdapter implements CvCatalogPort {
    private final JdbcClient jdbc;
    public JdbcCvCatalogAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public List<SkillMatch> findSkillsIn(String text) {
        var matches = new LinkedHashMap<UUID, SkillMatch>();
        jdbc.sql("""
                SELECT skill.public_id, skill.display_name, lower(unaccent(skill.display_name)) term
                FROM catalog.skill skill WHERE skill.active
                UNION ALL
                SELECT skill.public_id, skill.display_name, lower(unaccent(alias.alias::text)) term
                FROM catalog.skill skill JOIN catalog.skill_alias alias ON alias.skill_id=skill.id WHERE skill.active
                """).query((rs, row) -> new Term(rs.getObject("public_id", UUID.class),
                rs.getString("display_name"), rs.getString("term"))).list().forEach(term -> {
            if (containsTerm(text, term.term())) matches.putIfAbsent(term.id(), new SkillMatch(term.id(), term.name()));
        });
        return List.copyOf(matches.values());
    }

    private static boolean containsTerm(String text, String term) {
        String padded = " " + text.replaceAll("[^a-z0-9+#.]+", " ") + " ";
        return padded.contains(" " + term.replaceAll("[^a-z0-9+#.]+", " ").trim() + " ");
    }
    private record Term(UUID id, String name, String term) {}
}
