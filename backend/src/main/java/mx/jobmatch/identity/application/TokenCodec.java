package mx.jobmatch.identity.application;

import mx.jobmatch.identity.domain.EmailTokenPurpose;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

public class TokenCodec {
    private final byte[] secret;
    public TokenCodec(String secret) {
        if (secret.length() < 32) throw new IllegalArgumentException("Token secret must contain at least 32 characters");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String encode(UUID tokenId, EmailTokenPurpose purpose) {
        String id = tokenId.toString();
        return id + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac(id + ":" + purpose));
    }

    public Optional<UUID> validateAndReadId(String token, EmailTokenPurpose purpose) {
        if (token == null || token.length() > 200) return Optional.empty();
        int separator = token.indexOf('.');
        if (separator < 1) return Optional.empty();
        try {
            UUID id = UUID.fromString(token.substring(0, separator));
            byte[] supplied = Base64.getUrlDecoder().decode(token.substring(separator + 1));
            return MessageDigest.isEqual(supplied, mac(id + ":" + purpose)) ? Optional.of(id) : Optional.empty();
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    public String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private byte[] mac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot create security token", failure);
        }
    }
}

