package mx.jobmatch.identity.adapters.security;

import mx.jobmatch.identity.application.AccountRepository;
import mx.jobmatch.identity.application.IdentityNormalizer;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Profile("api")
@Service
public class AccountUserDetailsService implements UserDetailsService {
    private final AccountRepository accounts;

    public AccountUserDetailsService(AccountRepository accounts) { this.accounts = accounts; }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return accounts.findByEmail(IdentityNormalizer.email(email)).filter(account -> account.canAuthenticate())
                .map(account -> new AccountPrincipal(account.publicId(), account.passwordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
