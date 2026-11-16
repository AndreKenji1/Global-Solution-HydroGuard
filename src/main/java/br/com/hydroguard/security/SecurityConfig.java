package br.com.hydroguard.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean InMemoryUserDetailsManager users(
        @Value("${hydroguard.admin.username:admin}") String admin,
        @Value("${hydroguard.admin.password-hash:}") String adminHash,
        @Value("${hydroguard.analyst.username:analista}") String analyst,
        @Value("${hydroguard.analyst.password-hash:}") String analystHash) {
        List<UserDetails> users = new ArrayList<>();
        addUser(users, admin, adminHash, "ADMIN");
        addUser(users, analyst, analystHash, "ANALISTA");
        return new InMemoryUserDetailsManager(users);
    }

    private void addUser(List<UserDetails> users, String username, String hash, String role) {
        if (hash.isBlank()) return; // No fallback credentials: administrative access remains disabled.
        if (username.isBlank() || !hash.matches("\\$2[aby]\\$(1[0-6])\\$[./A-Za-z0-9]{53}"))
            throw new IllegalArgumentException("Configure usuário e hash BCrypt válidos (custo 10 a 16)");
        users.add(User.withUsername(username).password(hash).roles(role).build());
    }

    @Bean AuthenticationManager authenticationManager(InMemoryUserDetailsManager users, PasswordEncoder passwords) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwords);
        return new ProviderManager(provider);
    }

    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean CsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }

    @Bean SecurityFilterChain security(HttpSecurity http, ApiSecurityErrors errors,
                                      SecurityContextRepository contexts, CsrfTokenRepository csrf) throws Exception {
        return http
            .csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .securityContext(c -> c.securityContextRepository(contexts).requireExplicitSave(true))
            .requestCache(c -> c.disable())
            .formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,ex) -> errors.write(req,res,401,"Autenticação necessária"))
                .accessDeniedHandler((req,res,ex) -> errors.write(req,res,403,"Acesso negado ou token CSRF inválido")))
            .headers(h -> h
                .contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' blob: data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
                .referrerPolicy(c -> c.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .frameOptions(c -> c.deny())
                .permissionsPolicy(c -> c.policy("camera=(), microphone=(), geolocation=()")))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET,"/","/index.html","/styles.css","/app.js","/favicon.ico",
                    "/api/info","/api/dashboard","/api/auth/csrf","/actuator/health","/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/auth/login","/api/auth/logout","/api/ocorrencias","/api/ocorrencias/*/foto").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/ocorrencias/protocolo/*").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/auth/me").authenticated()
                .requestMatchers(HttpMethod.GET,"/api/admin/ocorrencias","/api/ocorrencias","/api/ocorrencias/*","/api/ocorrencias/*/foto").hasAnyRole("ADMIN","ANALISTA")
                .requestMatchers(HttpMethod.PATCH,"/api/ocorrencias/*/status").hasRole("ADMIN")
                .anyRequest().denyAll())
            .build();
    }
}
