package mx.jobmatch.operations.adapters.persistence;

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

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class JdbcBackgroundTaskAdapterIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    static HikariDataSource dataSource;
    static JdbcBackgroundTaskAdapter tasks;

    @BeforeAll
    static void setup() {
        var config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        tasks = new JdbcBackgroundTaskAdapter(JdbcClient.create(dataSource));
    }

    @AfterAll
    static void closePool() {
        if (dataSource != null) dataSource.close();
    }

    @Test
    void taskSurvivesAdapterAndCanOnlyBeClaimedOncePerLease() {
        var created = tasks.enqueue("DEMO", "{\"message\":\"hola\"}", "integration-key", Instant.now());
        var freshAdapter = new JdbcBackgroundTaskAdapter(JdbcClient.create(dataSource));

        var claimed = freshAdapter.claimNext("worker-a", Duration.ofMinutes(1));
        var competingClaim = freshAdapter.claimNext("worker-b", Duration.ofMinutes(1));

        assertThat(claimed).get().extracting(task -> task.publicId()).isEqualTo(created.publicId());
        assertThat(competingClaim).isEmpty();
        freshAdapter.complete(created.publicId(), "worker-a");
        assertThat(freshAdapter.find(created.publicId())).get()
                .extracting(task -> task.status()).isEqualTo(mx.jobmatch.operations.domain.BackgroundTask.Status.COMPLETED);
    }
}

