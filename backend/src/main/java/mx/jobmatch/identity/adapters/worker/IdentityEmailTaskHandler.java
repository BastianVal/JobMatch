package mx.jobmatch.identity.adapters.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.jobmatch.identity.application.AccountRepository;
import mx.jobmatch.identity.application.TokenCodec;
import mx.jobmatch.identity.domain.EmailTokenPurpose;
import mx.jobmatch.operations.application.BackgroundTaskHandler;
import mx.jobmatch.operations.domain.BackgroundTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Profile("worker")
@Component
public class IdentityEmailTaskHandler implements BackgroundTaskHandler {
    private final AccountRepository accounts;
    private final TokenCodec tokens;
    private final ObjectMapper objectMapper;
    private final JavaMailSender mailSender;
    private final String publicBaseUrl;
    private final String from;

    public IdentityEmailTaskHandler(AccountRepository accounts, TokenCodec tokens, ObjectMapper objectMapper,
                                    JavaMailSender mailSender,
                                    @Value("${jobmatch.security.public-base-url}") String publicBaseUrl,
                                    @Value("${jobmatch.mail.from}") String from) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.objectMapper = objectMapper;
        this.mailSender = mailSender;
        this.publicBaseUrl = publicBaseUrl;
        this.from = from;
    }

    @Override public boolean supports(String taskType) { return "SEND_IDENTITY_EMAIL".equals(taskType); }

    @Override
    public void handle(BackgroundTask task) {
        try {
            Payload payload = objectMapper.readValue(task.payload(), Payload.class);
            var account = accounts.findByPublicId(payload.accountId())
                    .orElseThrow(() -> new IllegalStateException("Email account no longer exists"));
            String rawToken = tokens.encode(payload.tokenId(), payload.purpose());
            String route = payload.purpose() == EmailTokenPurpose.VERIFY_EMAIL ? "/verify-email?token=" : "/reset-password?token=";
            String link = publicBaseUrl + route + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(account.email());
            if (payload.purpose() == EmailTokenPurpose.VERIFY_EMAIL) {
                message.setSubject("Verifica tu cuenta de JobMatch");
                message.setText("Verifica tu correo con este enlace:\n\n" + link + "\n\nEl enlace expira en 24 horas.");
            } else {
                message.setSubject("Restablece tu contraseña de JobMatch");
                message.setText("Restablece tu contraseña con este enlace:\n\n" + link + "\n\nEl enlace expira en 30 minutos.");
            }
            mailSender.send(message);
        } catch (java.io.IOException invalidPayload) {
            throw new IllegalArgumentException("Invalid identity email task payload", invalidPayload);
        }
    }

    private record Payload(UUID accountId, EmailTokenPurpose purpose, UUID tokenId) {}
}
