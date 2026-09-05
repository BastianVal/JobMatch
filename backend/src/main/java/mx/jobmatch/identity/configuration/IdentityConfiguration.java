package mx.jobmatch.identity.configuration;

import mx.jobmatch.identity.application.TokenCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class IdentityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
    }

    @Bean
    TokenCodec tokenCodec(@Value("${jobmatch.security.token-secret}") String secret) {
        return new TokenCodec(secret);
    }
}
