package br.com.hydroguard.controller;

import br.com.hydroguard.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record Login(@NotBlank @Size(max=100) String username, @NotBlank @Size(max=200) String password) {}
    private final AuthenticationManager auth;
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrf;
    private final LoginRateLimiter limiter;
    public AuthController(AuthenticationManager auth, SecurityContextRepository contexts, CsrfTokenRepository csrf, LoginRateLimiter limiter) {
        this.auth=auth; this.contexts=contexts; this.csrf=csrf; this.limiter=limiter;
    }
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) {
        return Map.of("token",token.getToken(),"headerName",token.getHeaderName(),"parameterName",token.getParameterName());
    }
    @PostMapping("/login") public Map<String,Object> login(@Valid @RequestBody Login input, HttpServletRequest req, HttpServletResponse res) {
        limiter.check(req.getRemoteAddr());
        if(input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Usuário ou senha inválidos");
        try {
            Authentication result=auth.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(input.username(),input.password()));
            new ChangeSessionIdAuthenticationStrategy().onAuthentication(result,req,res);
            var context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(result);SecurityContextHolder.setContext(context);
            contexts.saveContext(context,req,res);
            csrf.saveToken(null,req,res);
            return identity(result);
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Usuário ou senha inválidos");
        }
    }
    @GetMapping("/me") public Map<String,Object> me(Authentication authentication) { return identity(authentication); }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest req, HttpServletResponse res) {
        csrf.saveToken(null,req,res);
        var session=req.getSession(false);if(session!=null)session.invalidate();
        SecurityContextHolder.clearContext();
    }
    private Map<String,Object> identity(Authentication auth) {
        return Map.of("username",auth.getName(),"roles",auth.getAuthorities().stream()
            .map(a -> a.getAuthority().replaceFirst("^ROLE_", "")).toList());
    }
}
