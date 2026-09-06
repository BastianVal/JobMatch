package mx.jobmatch.catalog.adapters.persistence;

import mx.jobmatch.catalog.application.CatalogRoleResolver;
import mx.jobmatch.catalog.domain.RoleTitleMatcher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCatalogRoleResolver implements CatalogRoleResolver {
    private final JdbcClient jdbc;

    public JdbcCatalogRoleResolver(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Resolution> resolve(String title) {
        List<RoleTitleMatcher.Role> roles = jdbc.sql("""
                SELECT role.public_id, role.display_name,
                       ARRAY(SELECT alias.alias::text FROM catalog.role_alias alias
                             WHERE alias.role_family_id=role.id ORDER BY length(alias.alias::text) DESC, alias.alias) aliases
                FROM catalog.role_family role
                JOIN catalog.catalog_version version ON version.id=role.catalog_version_id
                WHERE role.active AND version.status='PUBLISHED'
                ORDER BY role.display_name, role.public_id
                """).query((rs, row) -> new RoleTitleMatcher.Role(rs.getObject("public_id", UUID.class),
                rs.getString("display_name"), array(rs.getArray("aliases")))).list();
        return RoleTitleMatcher.match(title, roles)
                .map(match -> new Resolution(match.roleFamilyId(), match.seniority()));
    }

    private static List<String> array(Array sqlArray) throws SQLException {
        return sqlArray == null ? List.of() : Arrays.asList((String[]) sqlArray.getArray());
    }
}
