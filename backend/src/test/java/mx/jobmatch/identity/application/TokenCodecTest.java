package mx.jobmatch.identity.application;

import mx.jobmatch.identity.domain.EmailTokenPurpose;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TokenCodecTest {
    private final TokenCodec codec = new TokenCodec("a-test-secret-with-at-least-thirty-two-characters");

    @Test
    void acceptsOnlyAuthenticTokenForExpectedPurpose() {
        UUID id = UUID.randomUUID();
        String token = codec.encode(id, EmailTokenPurpose.VERIFY_EMAIL);

        assertThat(codec.validateAndReadId(token, EmailTokenPurpose.VERIFY_EMAIL)).contains(id);
        assertThat(codec.validateAndReadId(token, EmailTokenPurpose.RESET_PASSWORD)).isEmpty();
        assertThat(codec.validateAndReadId(token + "tampered", EmailTokenPurpose.VERIFY_EMAIL)).isEmpty();
    }

    @Test
    void persistedHashDoesNotRevealRawToken() {
        String token = codec.encode(UUID.randomUUID(), EmailTokenPurpose.RESET_PASSWORD);
        assertThat(codec.hash(token)).hasSize(64).doesNotContain(token);
    }
}
