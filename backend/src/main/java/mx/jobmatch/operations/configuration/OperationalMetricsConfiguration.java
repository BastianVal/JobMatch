package mx.jobmatch.operations.configuration;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

/** Metrics that describe durable work rather than a single API process. */
@Profile("api")
@Configuration
public class OperationalMetricsConfiguration {
    private final JdbcClient jdbc;

    public OperationalMetricsConfiguration(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Bean
    MeterBinder operationalMetrics() {
        return registry -> {
            for (String status : List.of("PENDING", "RUNNING", "FAILED")) {
                Gauge.builder("jobmatch.background_tasks", () -> taskCount(status))
                        .tag("status", status.toLowerCase()).register(registry);
            }
            Gauge.builder("jobmatch.background_task_oldest_pending_seconds", this::oldestPendingSeconds)
                    .register(registry);
            for (String status : List.of("RUNNING", "FAILED")) {
                Gauge.builder("jobmatch.ingestion_sync_runs", () -> syncRunCount(status))
                        .tag("status", status.toLowerCase()).register(registry);
            }
            Gauge.builder("jobmatch.ingestion_failed_sync_runs_24h", this::failedSyncRunsLastDay)
                    .register(registry);
        };
    }

    private double taskCount(String status) {
        return safely(() -> jdbc.sql("SELECT count(*) FROM ops.background_task WHERE status=:status")
                .param("status", status).query(Long.class).single());
    }

    private double oldestPendingSeconds() {
        return safely(() -> jdbc.sql("""
                SELECT COALESCE(EXTRACT(EPOCH FROM now() - min(available_at)), 0)
                FROM ops.background_task WHERE status='PENDING' AND available_at <= now()
                """).query(Double.class).single());
    }

    private double syncRunCount(String status) {
        return safely(() -> jdbc.sql("SELECT count(*) FROM ingestion.sync_run WHERE status=:status")
                .param("status", status).query(Long.class).single());
    }

    private double failedSyncRunsLastDay() {
        return safely(() -> jdbc.sql("""
                SELECT count(*) FROM ingestion.sync_run
                WHERE status='FAILED' AND started_at >= now() - interval '24 hours'
                """).query(Long.class).single());
    }

    private static double safely(SqlGauge gauge) {
        try { return gauge.value(); }
        catch (RuntimeException ignored) { return Double.NaN; }
    }

    @FunctionalInterface
    private interface SqlGauge { double value(); }
}
