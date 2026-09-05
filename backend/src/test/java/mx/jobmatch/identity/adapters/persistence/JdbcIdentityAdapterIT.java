package mx.jobmatch.identity.adapters.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import mx.jobmatch.identity.domain.Account;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class JdbcIdentityAdapterIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    static HikariDataSource dataSource;
    static JdbcAccountAdapter accounts;

    @BeforeAll
    static void setup() {
        var config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        accounts = new JdbcAccountAdapter(JdbcClient.create(dataSource));
    }

    @AfterAll
    static void closePool() { if (dataSource != null) dataSource.close(); }

    @Test
    void accountMutationsRemainIsolatedByPublicIdentifier() {
        var owner = accounts.create(UUID.randomUUID(), "owner@example.com", "hash-one");
        var other = accounts.create(UUID.randomUUID(), "other@example.com", "hash-two");

        assertThat(accounts.activate(owner.publicId())).isTrue();
        assertThat(accounts.anonymizeAndMarkDeletionPending(owner.publicId())).isTrue();

        assertThat(accounts.findByPublicId(owner.publicId())).get()
                .extracting(Account::status).isEqualTo(Account.Status.DELETION_PENDING);
        assertThat(accounts.findByPublicId(other.publicId())).get()
                .extracting(Account::email, Account::status)
                .containsExactly("other@example.com", Account.Status.PENDING_VERIFICATION);
    }
}
