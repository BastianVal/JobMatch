package mx.jobmatch.operations.application;

import mx.jobmatch.operations.domain.BackgroundTask;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class DemoTaskService {
    private static final String OPERATION = "CREATE_DEMO_TASK";
    private final BackgroundTaskPort tasks;
    private final IdempotencyPort idempotency;

    public DemoTaskService(BackgroundTaskPort tasks, IdempotencyPort idempotency) {
        this.tasks = tasks;
        this.idempotency = idempotency;
    }

    @Transactional
    public BackgroundTask enqueue(String message, String key) {
        String payload = "{\"message\":\"" + escape(message) + "\"}";
        String hash = sha256(payload);
        return idempotency.find(OPERATION, key)
                .map(existing -> {
                    if (!existing.requestHash().equals(hash)) {
                        throw new IdempotencyConflictException();
                    }
                    return tasks.find(existing.resourcePublicId()).orElseThrow();
                })
                .orElseGet(() -> {
                    BackgroundTask task = tasks.enqueue("DEMO", payload, key, Instant.now());
                    idempotency.save(OPERATION, key, hash, task.publicId(), Instant.now().plusSeconds(86_400));
                    return task;
                });
    }

    public BackgroundTask find(UUID id) {
        return tasks.find(id).orElseThrow(TaskNotFoundException::new);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}

