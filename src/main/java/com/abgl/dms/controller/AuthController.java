package com.abgl.dms.controller;

import com.abgl.dms.auth.CaptchaValidationService;
import com.abgl.dms.auth.LoginAttemptService;
import com.abgl.dms.auth.LoginRequest;
import com.abgl.dms.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final CaptchaValidationService captchaValidationService;
    private final LoginAttemptService loginAttemptService;
    private final AppUserRepository appUserRepository;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    public AuthController(CaptchaValidationService captchaValidationService,
                          LoginAttemptService loginAttemptService,
                          AppUserRepository appUserRepository,
                          AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        this.captchaValidationService = captchaValidationService;
        this.loginAttemptService = loginAttemptService;
        this.appUserRepository = appUserRepository;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrfToken(CsrfToken csrfToken) {
        return Map.of("token", csrfToken.getToken());
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request,
                                      HttpServletRequest servletRequest,
                                      HttpServletResponse servletResponse) {
        log.debug("Login request received for username={}", request.username());
        try {
            captchaValidationService.validateAndConsume(request.challengeId(), request.captchaAnswer());

            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.username(), request.password()));

            loginAttemptService.recordSuccess(authentication.getName());

            sessionAuthenticationStrategy.onAuthentication(
                    authentication, servletRequest, servletResponse);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, servletRequest, servletResponse);

            log.info("Login successful for username={}", authentication.getName());
            return ResponseEntity.noContent().build();
        } catch (AuthenticationException exception) {
            if (exception instanceof BadCredentialsException) {
                loginAttemptService.recordFailure(request.username());
            }
            log.warn("Login rejected for username={}; reason={}",
                    request.username(), exception.getClass().getSimpleName());
            throw exception;
        } catch (ResponseStatusException exception) {
            log.warn("Login CAPTCHA rejected for username={}; status={}",
                    request.username(), exception.getStatusCode().value());
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Unexpected login failure for username={}", request.username(), exception);
            throw exception;
        }
    }

    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> session(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            log.debug("Session check found no authenticated user");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("authenticated", false));
        }

        String username = authentication.getName();
        String displayName = appUserRepository.findDisplayNameByUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated account has no matching application user record"));
        String initials = initialsFrom(displayName);

        log.debug("Session check succeeded for username={}", username);
        return ResponseEntity.ok(Map.of(
                "user", Map.of(
                        "displayName", displayName,
                        "initials", initials,
                        "username", username
                )
        ));
    }

    private String initialsFrom(String displayName) {
        String[] nameParts = displayName.trim().split("\\s+");
        StringBuilder initials = new StringBuilder(2);
        for (String namePart : nameParts) {
            if (!namePart.isEmpty()) {
                initials.appendCodePoint(namePart.codePointAt(0));
                if (initials.codePointCount(0, initials.length()) == 2) {
                    break;
                }
            }
        }
        return initials.toString().toUpperCase(java.util.Locale.ROOT);
    }
}
