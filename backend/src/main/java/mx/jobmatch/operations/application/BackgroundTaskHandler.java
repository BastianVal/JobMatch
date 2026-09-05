package mx.jobmatch.operations.application;

import mx.jobmatch.operations.domain.BackgroundTask;

public interface BackgroundTaskHandler {
    boolean supports(String taskType);
    void handle(BackgroundTask task);
}
