package mx.jobmatch.operations.adapters.worker;

import mx.jobmatch.operations.application.BackgroundTaskHandler;
import mx.jobmatch.operations.domain.BackgroundTask;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("worker")
@Component
public class DemoBackgroundTaskHandler implements BackgroundTaskHandler {
    @Override public boolean supports(String taskType) { return "DEMO".equals(taskType); }
    @Override public void handle(BackgroundTask task) { /* Intentionally empty platform smoke-test task. */ }
}
