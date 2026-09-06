package mx.jobmatch.catalog.adapters.persistence;

import mx.jobmatch.catalog.application.CatalogQueryPort;
import mx.jobmatch.catalog.domain.CatalogEntry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcCatalogQueryAdapter implements CatalogQueryPort {
    private final JdbcClient jdbc;

    public JdbcCatalogQueryAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public List<CatalogEntry> roles(String query, int limit) {
        return search("role_family", "role_alias", "role_family_id", "AND item.selectable", query, limit);
    }

    @Override
    public List<CatalogEntry> skills(String query, int limit) {
        return search("skill", "skill_alias", "skill_id", "", query, limit);
    }

    private List<CatalogEntry> search(String table, String aliasTable, String foreignKey, String selectableFilter,
                                      String query, int limit) {
        String term = query == null ? "" : query.strip();
        String sql = """
                SELECT item.public_id, item.slug, item.display_name, version.version_no,
                       ARRAY(SELECT alias.alias::text FROM catalog.%s alias WHERE alias.%s=item.id ORDER BY alias.alias) aliases
                FROM catalog.%s item
                JOIN catalog.catalog_version version ON version.id=item.catalog_version_id
                WHERE item.active AND version.status='PUBLISHED' %s
                  AND (:term='' OR unaccent(lower(item.display_name)) LIKE '%%' || unaccent(lower(:term)) || '%%'
                       OR EXISTS (SELECT 1 FROM catalog.%s alias WHERE alias.%s=item.id
                                  AND unaccent(lower(alias.alias::text)) LIKE '%%' || unaccent(lower(:term)) || '%%'))
                ORDER BY version.version_no DESC, item.display_name
                LIMIT :limit
                """.formatted(aliasTable, foreignKey, table, selectableFilter, aliasTable, foreignKey);
        return jdbc.sql(sql).param("term", term).param("limit", Math.max(1, Math.min(limit, 25)))
                .query((rs, row) -> new CatalogEntry(rs.getObject("public_id", UUID.class), rs.getString("slug"),
                        rs.getString("display_name"), array(rs.getArray("aliases")), rs.getInt("version_no"))).list();
    }

    @Override
    public int currentVersion() {
        return jdbc.sql("SELECT max(version_no) FROM catalog.catalog_version WHERE status='PUBLISHED'")
                .query(Integer.class).single();
    }

    private static List<String> array(Array sqlArray) throws SQLException {
        return sqlArray == null ? List.of() : Arrays.asList((String[]) sqlArray.getArray());
    }
}
