package mx.jobmatch.catalog.adapters.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class JdbcCatalogRoleResolverIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    static HikariDataSource dataSource;
    static JdbcClient jdbc;

    @BeforeAll
    static void setup() {
        var config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = JdbcClient.create(dataSource);
    }

    @AfterAll
    static void closePool() {
        if (dataSource != null) dataSource.close();
    }

    @Test
    void migrationPublishesTheNormalizedRoleCatalog() {
        assertThat(jdbc.sql("""
                SELECT count(*) FROM catalog.role_family role
                JOIN catalog.catalog_version version ON version.id=role.catalog_version_id
                WHERE version.version_no=2 AND version.status='PUBLISHED' AND role.selectable
                """).query(Integer.class).single()).isEqualTo(42);
        assertThat(jdbc.sql("SELECT count(*) FROM catalog.role_alias").query(Integer.class).single()).isEqualTo(165);
    }

    @Test
    void searchFindsContainedTermsAndAliases() {
        var catalog = new JdbcCatalogQueryAdapter(jdbc);
        assertThat(catalog.roles("java", 10)).extracting("displayName").containsExactly("Desarrollador Java");
        assertThat(catalog.roles("developer", 25)).isNotEmpty();
        assertThat(catalog.roles("datos", 25)).extracting("displayName")
                .contains("Analista de Datos", "Científico de Datos", "Ingeniero de Datos");
    }

    @Test
    void resolverUsesAliasesAndExtractsSeniority() {
        var result = new JdbcCatalogRoleResolver(jdbc).resolve("Senior Java Developer").orElseThrow();
        assertThat(result.roleFamilyId().toString()).isEqualTo("11100000-0000-0000-0000-000000000001");
        assertThat(result.seniority()).isEqualTo("SENIOR");
    }
}
