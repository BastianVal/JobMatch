package mx.jobmatch.operations.application;

import mx.jobmatch.operations.domain.BackgroundTask;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoTaskServiceTest {
    private final FakeTasks tasks = new FakeTasks();
    private final FakeIdempotency idempotency = new FakeIdempotency();
    private final DemoTaskService service = new DemoTaskService(tasks, idempotency);

    @Test
    void sameKeyAndBodyReturnsTheSameTask() {
        var first = service.enqueue("hola", "same-key");
        var repeated = service.enqueue("hola", "same-key");

        assertThat(repeated.publicId()).isEqualTo(first.publicId());
        assertThat(tasks.items).hasSize(1);
    }

    @Test
    void sameKeyWithDifferentBodyIsRejected() {
        service.enqueue("uno", "same-key");

        assertThatThrownBy(() -> service.enqueue("otro", "same-key"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    private static class FakeTasks implements BackgroundTaskPort {
        private final Map<UUID, BackgroundTask> items = new HashMap<>();
        public BackgroundTask enqueue(String type, String payload, String key, Instant at) {
            var task = new BackgroundTask(UUID.randomUUID(), type, payload, key, BackgroundTask.Status.PENDING, 0, at);
            items.put(task.publicId(), task);
            return task;
        }
        public Optional<BackgroundTask> find(UUID id) { return Optional.ofNullable(items.get(id)); }
        public Optional<BackgroundTask> claimNext(String worker, Duration lease) { return Optional.empty(); }
        public void complete(UUID id, String worker) {}
        public void retry(UUID id, String worker, String error, Instant at) {}
    }

    private static class FakeIdempotency implements IdempotencyPort {
        private final Map<String, Record> items = new HashMap<>();
        public Optional<Record> find(String operation, String key) { return Optional.ofNullable(items.get(operation + key)); }
        public void save(String operation, String key, String hash, UUID id, Instant expiresAt) {
            items.put(operation + key, new Record(hash, id));
        }
    }
}

