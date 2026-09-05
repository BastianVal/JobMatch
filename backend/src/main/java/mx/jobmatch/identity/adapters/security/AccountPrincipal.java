package mx.jobmatch.identity.adapters.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record AccountPrincipal(UUID accountId, String passwordHash) implements UserDetails {
    @Serial private static final long serialVersionUID = 1L;

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return accountId.toString(); }
}
