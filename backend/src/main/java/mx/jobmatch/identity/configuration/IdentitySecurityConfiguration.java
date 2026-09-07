package mx.jobmatch.identity.configuration;

import mx.jobmatch.identity.adapters.security.AbsoluteSessionTimeoutFilter;
import mx.jobmatch.identity.adapters.security.SecurityProblemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Profile("api")
@Configuration
public class IdentitySecurityConfiguration {
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder passwords) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwords);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemWriter problems,
                                            AbsoluteSessionTimeoutFilter absoluteTimeout) throws Exception {
        HttpSessionCsrfTokenRepository csrf = new HttpSessionCsrfTokenRepository();
        csrf.setHeaderName("X-CSRF-TOKEN");
        http
                .csrf(configurer -> configurer.csrfTokenRepository(csrf))
                .cors(configurer -> configurer.disable())
                .httpBasic(configurer -> configurer.disable())
                .formLogin(configurer -> configurer.disable())
                .logout(configurer -> configurer.disable())
                .requestCache(configurer -> configurer.disable())
                .sessionManagement(configurer -> configurer
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/api/v1/auth/csrf",
                                "/api/v1/auth/register", "/api/v1/auth/verify-email",
                                "/api/v1/auth/login", "/api/v1/auth/password/forgot",
                                "/api/v1/auth/password/reset").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, failure) -> problems.write(response, 401,
                                "AUTHENTICATION_REQUIRED", "Debes iniciar sesión."))
                        .accessDeniedHandler((request, response, failure) -> problems.write(response, 403,
                                "ACCESS_DENIED", "La solicitud no está autorizada.")))
                .addFilterBefore(absoluteTimeout, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
