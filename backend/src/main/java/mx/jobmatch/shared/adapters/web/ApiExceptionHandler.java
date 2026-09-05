package mx.jobmatch.shared.adapters.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import mx.jobmatch.operations.application.IdempotencyConflictException;
import mx.jobmatch.operations.application.TaskNotFoundException;
import mx.jobmatch.identity.application.IdentityExceptions.AccountNotFound;
import mx.jobmatch.identity.application.IdentityExceptions.AuthenticationRateLimited;
import mx.jobmatch.identity.application.IdentityExceptions.InvalidCredentials;
import mx.jobmatch.identity.application.IdentityExceptions.InvalidPassword;
import mx.jobmatch.identity.application.IdentityExceptions.InvalidToken;
import mx.jobmatch.profile.application.ProfileExceptions.InvalidCatalogReference;
import mx.jobmatch.profile.application.ProfileExceptions.InvalidProfile;
import mx.jobmatch.profile.application.ProfileExceptions.VersionConflict;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        var fields = ex.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(
                error -> error.getField(), error -> error.getDefaultMessage(), (first, ignored) -> first));
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "La solicitud contiene datos inválidos.", fields, false);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotencyConflict() {
        return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "La clave ya fue usada con otra solicitud.", null, false);
    }

    @ExceptionHandler(TaskNotFoundException.class)
    ProblemDetail notFound() {
        return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "El recurso no existe.", null, false);
    }

    @ExceptionHandler(AccountNotFound.class)
    ProblemDetail accountNotFound() {
        return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "El recurso no existe.", null, false);
    }

    @ExceptionHandler(InvalidToken.class)
    ProblemDetail invalidToken() {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_OR_EXPIRED_TOKEN", "El enlace no es válido o expiró.", null, false);
    }

    @ExceptionHandler(InvalidPassword.class)
    ProblemDetail invalidPassword() {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "La contraseña debe tener entre 12 y 128 caracteres.", null, false);
    }

    @ExceptionHandler(InvalidCredentials.class)
    ProblemDetail invalidCredentials() {
        return problem(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "El correo o la contraseña son incorrectos.", null, false);
    }

    @ExceptionHandler(AuthenticationRateLimited.class)
    ProblemDetail authenticationRateLimited() {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Demasiados intentos. Intenta más tarde.", null, true);
    }

    @ExceptionHandler(VersionConflict.class)
    ProblemDetail versionConflict() {
        return problem(HttpStatus.CONFLICT, "VERSION_CONFLICT", "El perfil cambió; vuelve a cargarlo antes de guardar.", null, false);
    }

    @ExceptionHandler(InvalidCatalogReference.class)
    ProblemDetail invalidCatalogReference() {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "El rol o habilidad de catálogo no es válido.", null, false);
    }

    @ExceptionHandler(InvalidProfile.class)
    ProblemDetail invalidProfile(InvalidProfile failure) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", failure.getMessage(), null, false);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException ex) {
        var fields = ex.getConstraintViolations().stream().collect(Collectors.toMap(
                violation -> violation.getPropertyPath().toString(), violation -> violation.getMessage(),
                (first, ignored) -> first));
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "La solicitud contiene datos inválidos.", fields, false);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail internal(Exception ex) {
        log.error("Unhandled request failure traceId={}", MDC.get("traceId"), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Ocurrió un error interno.", null, true);
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail, Map<String, String> fields, boolean retryable) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://jobmatch.mx/problems/" + code.toLowerCase().replace('_', '-')));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setProperty("traceId", MDC.get("traceId"));
        problem.setProperty("fieldErrors", fields);
        problem.setProperty("retryable", retryable);
        return problem;
    }
}
