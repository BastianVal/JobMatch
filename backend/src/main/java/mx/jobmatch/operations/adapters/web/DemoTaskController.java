package mx.jobmatch.operations.adapters.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mx.jobmatch.operations.application.DemoTaskService;
import mx.jobmatch.operations.domain.BackgroundTask;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.UUID;

@Profile("api")
@Validated
@RestController
@RequestMapping("/api/v1/operations/demo-tasks")
public class DemoTaskController {
    private final DemoTaskService service;
    public DemoTaskController(DemoTaskService service) { this.service = service; }

    @PostMapping
    ResponseEntity<Response> enqueue(@RequestHeader("Idempotency-Key") @NotBlank @Size(max=200) String key,
                                     @Valid @RequestBody Request request) {
        BackgroundTask task = service.enqueue(request.message(), key);
        return ResponseEntity.accepted().location(URI.create("/api/v1/operations/demo-tasks/" + task.publicId()))
                .body(Response.from(task));
    }

    @GetMapping("/{id}")
    Response get(@PathVariable UUID id) { return Response.from(service.find(id)); }

    record Request(@NotBlank @Size(max=500) String message) {}
    record Response(UUID publicId, String type, String status, int attempts) {
        static Response from(BackgroundTask task) {
            return new Response(task.publicId(), task.type(), task.status().name(), task.attempts());
        }
    }
}
