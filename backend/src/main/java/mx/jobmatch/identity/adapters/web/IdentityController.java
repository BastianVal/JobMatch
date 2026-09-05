package mx.jobmatch.identity.adapters.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import mx.jobmatch.identity.adapters.security.AccountPrincipal;
import mx.jobmatch.identity.application.AuthenticationRateLimiter;
import mx.jobmatch.identity.application.IdentityService;
import mx.jobmatch.identity.domain.Account;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static mx.jobmatch.identity.application.IdentityExceptions.InvalidCredentials;

@Profile("api")
@RestController
@RequestMapping("/api/v1")
public class IdentityController {
    private final IdentityService identities;
    private final AuthenticationManager authenticationManager;
    private final AuthenticationRateLimiter rateLimiter;
    private final SecurityContextRepository securityContexts;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public IdentityController(IdentityService identities, AuthenticationManager authenticationManager,
                              AuthenticationRateLimiter rateLimiter, SecurityContextRepository securityContexts) {
        this.identities = identities;
        this.authenticationManager = authenticationManager;
        this.rateLimiter = rateLimiter;
        this.securityContexts = securityContexts;
    }

    @GetMapping("/auth/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void register(@Valid @RequestBody Registration request) {
        identities.register(request.email(), request.password());
    }

    @PostMapping("/auth/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verify(@Valid @RequestBody TokenRequest request) { identities.verify(request.token()); }

    @PostMapping("/auth/login")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void login(@Valid @RequestBody Login request, HttpServletRequest servletRequest,
               HttpServletResponse servletResponse) {
        var key = rateLimiter.check(request.email(), servletRequest.getRemoteAddr());
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
            servletRequest.getSession(true);
            servletRequest.changeSessionId();
            var context = contextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            contextHolder.setContext(context);
            securityContexts.saveContext(context, servletRequest, servletResponse);
            rateLimiter.succeeded(key);
        } catch (AuthenticationException failure) {
            rateLimiter.failed(key);
            throw new InvalidCredentials();
        }
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        contextHolder.clearContext();
    }

    @PostMapping("/auth/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void forgot(@Valid @RequestBody EmailRequest request) { identities.requestPasswordReset(request.email()); }

    @PostMapping("/auth/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@Valid @RequestBody PasswordReset request) {
        identities.resetPassword(request.token(), request.newPassword());
    }

    @GetMapping("/me/account")
    AccountResponse account(@AuthenticationPrincipal AccountPrincipal principal) { return AccountResponse.from(identities.get(principal.accountId())); }

    @DeleteMapping("/me/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AccountPrincipal principal, HttpServletRequest request) {
        identities.delete(principal.accountId());
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        contextHolder.clearContext();
    }

    public record Registration(@NotBlank @Email String email, @NotBlank String password) {}
    public record Login(@NotBlank @Email String email, @NotBlank String password) {}
    public record EmailRequest(@NotBlank @Email String email) {}
    public record TokenRequest(@NotBlank String token) {}
    public record PasswordReset(@NotBlank String token, @NotBlank String newPassword) {}
    public record CsrfResponse(String headerName, String token) {}
    public record AccountResponse(java.util.UUID id, String email, Account.Status status) {
        static AccountResponse from(Account account) { return new AccountResponse(account.publicId(), account.email(), account.status()); }
    }
}
