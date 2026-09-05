package mx.jobmatch.identity.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import static mx.jobmatch.identity.application.IdentityExceptions.AuthenticationRateLimited;

@Profile("api")
@Service
public class AuthenticationRateLimiter {
    private final AuthAttemptPort attempts;
    private final byte[] pepper;

    public AuthenticationRateLimiter(AuthAttemptPort attempts,
                                     @Value("${jobmatch.security.rate-limit-pepper}") String pepper) {
        this.attempts = attempts;
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    public AttemptKey check(String email, String ipAddress) {
        AttemptKey key = key(email, ipAddress);
        Instant since = Instant.now().minus(15, ChronoUnit.MINUTES);
        if (attempts.countEmailFailuresSince(key.emailHash(), since) >= 5
                || attempts.countIpFailuresSince(key.ipHash(), since) >= 30) {
            attempts.record(key.emailHash(), key.ipHash(), AuthAttemptPort.Result.BLOCKED);
            throw new AuthenticationRateLimited();
        }
        return key;
    }

    public void succeeded(AttemptKey key) { attempts.record(key.emailHash(), key.ipHash(), AuthAttemptPort.Result.SUCCESS); }
    public void failed(AttemptKey key) { attempts.record(key.emailHash(), key.ipHash(), AuthAttemptPort.Result.FAILURE); }

    private AttemptKey key(String email, String ipAddress) {
        return new AttemptKey(hash(IdentityNormalizer.email(email)), hash(ipAddress == null ? "unknown" : ipAddress));
    }

    private String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot protect authentication telemetry", failure);
        }
    }

    public record AttemptKey(String emailHash, String ipHash) {}
}
