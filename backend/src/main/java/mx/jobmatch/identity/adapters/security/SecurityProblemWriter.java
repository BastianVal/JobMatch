package mx.jobmatch.identity.adapters.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SecurityProblemWriter {
    private final ObjectMapper objectMapper;

    public SecurityProblemWriter(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public void write(HttpServletResponse response, int status, String code, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://jobmatch.mx/problems/" + code.toLowerCase().replace('_', '-'));
        body.put("title", status == 401 ? "Unauthorized" : "Forbidden");
        body.put("status", status);
        body.put("detail", detail);
        body.put("code", code);
        body.put("traceId", MDC.get("traceId"));
        body.put("retryable", false);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
