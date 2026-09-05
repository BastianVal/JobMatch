package mx.jobmatch.operations.adapters.worker;

import mx.jobmatch.operations.application.BackgroundTaskPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Profile("worker")
@Component
public class DemoTaskWorker {
    private final BackgroundTaskPort tasks;
    private final TransactionTemplate transactions;
    private final Duration lease;
    private final String workerId;

    public DemoTaskWorker(BackgroundTaskPort tasks, TransactionTemplate transactions,
                          @Value("${jobmatch.worker.lease-seconds:60}") long leaseSeconds) {
        this.tasks = tasks;
        this.transactions = transactions;
        this.lease = Duration.ofSeconds(leaseSeconds);
        this.workerId = hostname() + ":" + UUID.randomUUID();
    }

    @Scheduled(fixedDelayString = "${jobmatch.worker.poll-delay-ms:1000}")
    public void poll() {
        var task = transactions.execute(status -> tasks.claimNext(workerId, lease));
        task.ifPresent(claimed -> {
            try {
                // Handler demostrativo: la persistencia del estado prueba entrega durable.
                transactions.executeWithoutResult(status -> tasks.complete(claimed.publicId(), workerId));
            } catch (RuntimeException failure) {
                transactions.executeWithoutResult(status -> tasks.retry(claimed.publicId(), workerId,
                        "DEMO_HANDLER_FAILED", Instant.now().plusSeconds(5)));
            }
        });
    }

    private static String hostname() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception ignored) { return "worker"; }
    }
}

