package mx.jobmatch.identity.application;

import mx.jobmatch.identity.domain.Account;
import mx.jobmatch.operations.application.BackgroundTaskPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IdentityServiceTest {
    AccountRepository accounts = mock(AccountRepository.class);
    EmailTokenRepository tokens = mock(EmailTokenRepository.class);
    BackgroundTaskPort tasks = mock(BackgroundTaskPort.class);
    SessionRevocationPort sessions = mock(SessionRevocationPort.class);
    PasswordEncoder passwords = mock(PasswordEncoder.class);
    TokenCodec codec = new TokenCodec("a-test-secret-with-at-least-thirty-two-characters");
    PersonalDataDeletionPort personalData = mock(PersonalDataDeletionPort.class);
    IdentityService service = new IdentityService(accounts, tokens, tasks, sessions, passwords, codec, List.of(personalData));

    @BeforeEach
    void defaults() {
        when(passwords.encode(any())).thenReturn("argon2-hash");
    }

    @Test
    void registrationNormalizesEmailAndNeverQueuesRawPassword() {
        UUID accountId = UUID.randomUUID();
        when(accounts.findByEmail("person@example.com")).thenReturn(Optional.empty());
        when(accounts.create(any(), eq("person@example.com"), eq("argon2-hash")))
                .thenReturn(new Account(accountId, "person@example.com", "argon2-hash",
                        Account.Status.PENDING_VERIFICATION, 0));

        service.register("  Person@Example.COM ", "correct horse battery");

        verify(accounts).create(any(), eq("person@example.com"), eq("argon2-hash"));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(tasks).enqueue(eq("SEND_IDENTITY_EMAIL"), payload.capture(), any(), any());
        assertThat(payload.getValue()).contains(accountId.toString()).doesNotContain("correct horse battery");
    }

    @Test
    void deletionTargetsOnlyAuthenticatedAccountAndRevokesItsSessions() {
        UUID owner = UUID.randomUUID();
        UUID anotherAccount = UUID.randomUUID();
        when(accounts.anonymizeAndMarkDeletionPending(owner)).thenReturn(true);

        service.delete(owner);

        verify(accounts).anonymizeAndMarkDeletionPending(owner);
        verify(accounts, never()).anonymizeAndMarkDeletionPending(anotherAccount);
        verify(tokens).deleteForAccount(owner);
        verify(sessions).revokeAll(owner);
        verify(personalData).deleteForAccount(owner);
    }
}
