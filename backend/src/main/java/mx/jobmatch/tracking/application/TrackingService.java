package mx.jobmatch.tracking.application;

import mx.jobmatch.operations.application.IdempotencyConflictException;
import mx.jobmatch.operations.application.IdempotencyPort;
import mx.jobmatch.tracking.domain.JobActivity;
import mx.jobmatch.tracking.domain.TrackedJob;
import mx.jobmatch.tracking.domain.TrackingState;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static mx.jobmatch.tracking.application.TrackingExceptions.*;

@Profile("api")
@Service
public class TrackingService {
    private final TrackingRepository tracking;
    private final IdempotencyPort idempotency;

    public TrackingService(TrackingRepository tracking, IdempotencyPort idempotency) {
        this.tracking = tracking; this.idempotency = idempotency;
    }

    @Transactional
    public int recordImpressions(UUID accountId, List<UUID> requested) {
        List<UUID> ids = uniqueIds(requested);
        int recorded = tracking.recordImpressions(accountId, ids);
        if (recorded != ids.size()) throw new ResourceNotFound();
        return recorded;
    }

    @Transactional(readOnly = true)
    public List<JobActivity> activities(UUID accountId, List<UUID> requested) {
        List<UUID> ids = uniqueIds(requested);
        List<JobActivity> found = tracking.activities(accountId, ids);
        if (found.size() != ids.size()) throw new ResourceNotFound();
        return found;
    }

    @Transactional(readOnly = true)
    public TrackedJob get(UUID accountId, UUID jobId) {
        return tracking.find(accountId, jobId).orElseThrow(ResourceNotFound::new);
    }

    @Transactional(readOnly = true)
    public List<TrackedJob> list(UUID accountId, List<TrackingState> states, int limit) {
        if (limit < 1 || limit > 100) throw new InvalidTrackingRequest("El límite debe estar entre 1 y 100.");
        var selected = states == null || states.isEmpty()
                ? List.of(TrackingState.APPLIED, TrackingState.INTERVIEW, TrackingState.REJECTED, TrackingState.WITHDRAWN)
                : states.stream().filter(state -> state != TrackingState.DISCARDED).distinct().toList();
        return selected.isEmpty() ? List.of() : tracking.list(accountId, selected, limit);
    }

    @Transactional
    public TrackedJob transition(UUID accountId, UUID jobId, TrackingState target, String note,
                                 long expectedVersion, String idempotencyKey) {
        validate(target, note, expectedVersion, idempotencyKey);
        String operation = "tracking:" + accountId + ":" + jobId;
        String requestHash = hash(expectedVersion + "\n" + target + "\n" + (note == null ? "" : note));
        var previous = idempotency.find(operation, idempotencyKey);
        if (previous.isPresent()) {
            if (!previous.get().requestHash().equals(requestHash)) throw new IdempotencyConflictException();
            return tracking.findById(accountId, previous.get().resourcePublicId()).orElseThrow(ResourceNotFound::new);
        }
        TrackedJob current = tracking.find(accountId, jobId).orElse(null);
        TrackedJob result;
        try {
            if (current == null) {
                if (expectedVersion != 0) throw new VersionConflict();
                if (!TrackingState.canStartAt(target)) throw new InvalidTransition("La postulación debe iniciar guardada, descartada o aplicada.");
                result = tracking.create(accountId, jobId, target, clean(note));
            } else {
                if (current.version() != expectedVersion) throw new VersionConflict();
                if (!current.state().canTransitionTo(target)) {
                    throw new InvalidTransition("La transición de " + current.state() + " a " + target + " no está permitida.");
                }
                if (current.state() == target && java.util.Objects.equals(current.note(), clean(note))) result = current;
                else result = tracking.update(accountId, jobId, expectedVersion, current.state(), target, clean(note))
                        .orElseThrow(VersionConflict::new);
            }
            idempotency.save(operation, idempotencyKey, requestHash, result.id(), Instant.now().plusSeconds(86_400));
            return result;
        } catch (DataIntegrityViolationException race) {
            throw new VersionConflict();
        }
    }

    private static void validate(TrackingState target, String note, long version, String key) {
        if (target == null) throw new InvalidTrackingRequest("El estado es obligatorio.");
        if (version < 0) throw new InvalidTrackingRequest("If-Match no es válido.");
        if (note != null && note.strip().length() > 2000) throw new InvalidTrackingRequest("La nota no puede exceder 2000 caracteres.");
        if (key == null || key.isBlank() || key.length() > 200) throw new InvalidTrackingRequest("Idempotency-Key es obligatoria y admite hasta 200 caracteres.");
    }

    private static List<UUID> uniqueIds(List<UUID> requested) {
        if (requested == null || requested.isEmpty()) throw new InvalidTrackingRequest("Incluye al menos una vacante.");
        var ids = new LinkedHashSet<>(requested);
        if (ids.contains(null) || ids.size() > 100) throw new InvalidTrackingRequest("Se admiten hasta 100 vacantes válidas.");
        return List.copyOf(ids);
    }

    private static String clean(String note) { return note == null || note.isBlank() ? null : note.strip(); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
