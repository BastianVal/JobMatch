package mx.jobmatch.operations.adapters.worker;

import mx.jobmatch.operations.application.BackgroundTaskPort;
import mx.jobmatch.operations.application.BackgroundTaskHandler;
import mx.jobmatch.operations.application.NonRetryableTaskException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Profile("worker")
@Component
public class DemoTaskWorker {
    private final BackgroundTaskPort tasks;
    private final TransactionTemplate transactions;
    private final List<BackgroundTaskHandler> handlers;
    private final Duration lease;
    private final String workerId;

    public DemoTaskWorker(BackgroundTaskPort tasks, TransactionTemplate transactions,
                          List<BackgroundTaskHandler> handlers,
                          @Value("${jobmatch.worker.lease-seconds:60}") long leaseSeconds) {
        this.tasks = tasks;
        this.transactions = transactions;
        this.handlers = handlers;
        this.lease = Duration.ofSeconds(leaseSeconds);
        this.workerId = hostname() + ":" + UUID.randomUUID();
    }

    @Scheduled(fixedDelayString = "${jobmatch.worker.poll-delay-ms:1000}")
    public void poll() {
        var task = transactions.execute(status -> tasks.claimNext(workerId, lease));
        task.ifPresent(claimed -> {
            try {
                handlers.stream().filter(handler -> handler.supports(claimed.type())).findFirst()
                        .orElseThrow(() -> new IllegalStateException("No handler for " + claimed.type()))
                        .handle(claimed);
                transactions.executeWithoutResult(status -> tasks.complete(claimed.publicId(), workerId));
            } catch (NonRetryableTaskException failure) {
                transactions.executeWithoutResult(status -> tasks.fail(claimed.publicId(), workerId,
                        failure.safeCode()));
            } catch (RuntimeException failure) {
                long delaySeconds = Math.min(60, 1L << Math.min(6, Math.max(0, claimed.attempts() - 1)));
                long jitter = Math.floorMod(claimed.publicId().hashCode(), Math.max(1, delaySeconds));
                transactions.executeWithoutResult(status -> tasks.retry(claimed.publicId(), workerId,
                        "TASK_HANDLER_FAILED", Instant.now().plusSeconds(delaySeconds + jitter)));
            }
        });
    }

    private static String hostname() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception ignored) { return "worker"; }
    }
}
