package mx.jobmatch.identity.application;

import mx.jobmatch.identity.domain.Account;
import mx.jobmatch.identity.domain.EmailTokenPurpose;
import mx.jobmatch.operations.application.BackgroundTaskPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import static mx.jobmatch.identity.application.IdentityExceptions.*;

@Profile("api")
@Service
public class IdentityService {
    private final AccountRepository accounts;
    private final EmailTokenRepository tokens;
    private final BackgroundTaskPort tasks;
    private final SessionRevocationPort sessions;
    private final PasswordEncoder passwords;
    private final TokenCodec tokenCodec;
    private final List<PersonalDataDeletionPort> personalDataDeleters;

    public IdentityService(AccountRepository accounts, EmailTokenRepository tokens, BackgroundTaskPort tasks,
                           SessionRevocationPort sessions, PasswordEncoder passwords, TokenCodec tokenCodec,
                           List<PersonalDataDeletionPort> personalDataDeleters) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.tasks = tasks;
        this.sessions = sessions;
        this.passwords = passwords;
        this.tokenCodec = tokenCodec;
        this.personalDataDeleters = personalDataDeleters;
    }

    @Transactional
    public void register(String email, String password) {
        validatePassword(password);
        String normalized = IdentityNormalizer.email(email);
        if (accounts.findByEmail(normalized).isPresent()) return;
        Account account = accounts.create(UUID.randomUUID(), normalized, passwords.encode(password));
        issueEmailToken(account.publicId(), EmailTokenPurpose.VERIFY_EMAIL, 86_400);
    }

    @Transactional
    public void verify(String rawToken) {
        var record = resolve(rawToken, EmailTokenPurpose.VERIFY_EMAIL);
        if (!accounts.activate(record.accountId()) || !tokens.consume(record.tokenId(), Instant.now())) throw new InvalidToken();
    }

    @Transactional
    public void requestPasswordReset(String email) {
        accounts.findByEmail(IdentityNormalizer.email(email))
                .filter(Account::canAuthenticate)
                .ifPresent(account -> issueEmailToken(account.publicId(), EmailTokenPurpose.RESET_PASSWORD, 1_800));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        validatePassword(newPassword);
        var record = resolve(rawToken, EmailTokenPurpose.RESET_PASSWORD);
        if (!accounts.updatePassword(record.accountId(), passwords.encode(newPassword))
                || !tokens.consume(record.tokenId(), Instant.now())) throw new InvalidToken();
        sessions.revokeAll(record.accountId());
    }

    @Transactional(readOnly = true)
    public Account get(UUID accountId) {
        return accounts.findByPublicId(accountId).orElseThrow(AccountNotFound::new);
    }

    @Transactional
    public void delete(UUID accountId) {
        personalDataDeleters.forEach(deleter -> deleter.deleteForAccount(accountId));
        if (!accounts.anonymizeAndMarkDeletionPending(accountId)) throw new AccountNotFound();
        tokens.deleteForAccount(accountId);
        sessions.revokeAll(accountId);
    }

    private EmailTokenRepository.TokenRecord resolve(String rawToken, EmailTokenPurpose purpose) {
        UUID tokenId = tokenCodec.validateAndReadId(rawToken, purpose).orElseThrow(InvalidToken::new);
        return tokens.findUsable(tokenCodec.hash(rawToken), purpose, Instant.now())
                .filter(record -> record.tokenId().equals(tokenId)).orElseThrow(InvalidToken::new);
    }

    private void issueEmailToken(UUID accountId, EmailTokenPurpose purpose, long lifetimeSeconds) {
        UUID tokenId = UUID.randomUUID();
        String rawToken = tokenCodec.encode(tokenId, purpose);
        tokens.save(tokenId, accountId, purpose, tokenCodec.hash(rawToken), Instant.now().plusSeconds(lifetimeSeconds));
        String payload = "{\"accountId\":\"" + accountId + "\",\"purpose\":\"" + purpose
                + "\",\"tokenId\":\"" + tokenId + "\"}";
        tasks.enqueue("SEND_IDENTITY_EMAIL", payload, purpose + ":" + tokenId, Instant.now());
    }

    static void validatePassword(String password) {
        if (password == null) throw new InvalidPassword();
        int length = password.codePointCount(0, password.length());
        if (length < 12 || length > 128) throw new InvalidPassword();
    }
}
