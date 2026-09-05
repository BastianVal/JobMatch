package mx.jobmatch.discovery.application;

import mx.jobmatch.discovery.application.DiscoveryExceptions.InvalidSearch;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public record SearchCursor(BigDecimal relevance, Instant publishedAt, UUID jobId) {
    public static String encode(BigDecimal relevance, Instant publishedAt, UUID jobId) {
        String raw = relevance.toPlainString() + "|" + publishedAt + "|" + jobId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static SearchCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 3) throw new IllegalArgumentException();
            return new SearchCursor(new BigDecimal(parts[0]), Instant.parse(parts[1]),
                    UUID.fromString(parts[2]));
        } catch (RuntimeException invalid) {
            throw new InvalidSearch("El cursor no es válido.");
        }
    }
}
