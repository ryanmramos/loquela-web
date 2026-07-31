package com.example.whatsanki.controller;

import com.example.whatsanki.dto.BrowserSessionDto;
import com.example.whatsanki.dto.LocalLoginRequestDto;
import com.example.whatsanki.service.BrowserSessionService;
import com.example.whatsanki.service.security.BrowserAuthenticationSuccessHandler;
import com.example.whatsanki.service.UserIdentityService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.time.Instant;

/**
 * Creates normal server-side sessions for fixed, non-secret local accounts.
 * Both the profile and property conditions keep this boundary out of production.
 */
@Hidden
@RestController
@Profile({"local", "test"})
@ConditionalOnProperty(
        prefix = "whatsanki.security",
        name = "local-login-enabled",
        havingValue = "true"
)
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class LocalBrowserAuthenticationController {

    private static final Map<String, LocalAccount> ACCOUNTS = Map.of(
            "learner", new LocalAccount("local:learner", "learner@local.loquela.test", "Local Learner"),
            "reviewer", new LocalAccount("local:reviewer", "reviewer@local.loquela.test", "Local Reviewer")
    );

    private final UserIdentityService userIdentityService;
    private final BrowserSessionService browserSessionService;

    @PostMapping("/local-login")
    public ResponseEntity<BrowserSessionDto> login(
            @Valid @RequestBody LocalLoginRequestDto requestDto,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        LocalAccount account = ACCOUNTS.get(requestDto.accountId());
        if (account == null) {
            throw new IllegalArgumentException("Unsupported local account");
        }

        userIdentityService.resolveOrCreateGoogle(
                account.subject(),
                account.email(),
                account.displayName()
        );
        request.getSession();
        request.changeSessionId();
        request.getSession().setAttribute(
                BrowserAuthenticationSuccessHandler.RECENT_AUTHENTICATION_AT,
                Instant.now().toEpochMilli()
        );

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        account.subject(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);

        return ResponseEntity.ok(browserSessionService.getByGoogleSubject(account.subject()));
    }

    private record LocalAccount(String subject, String email, String displayName) {
    }
}
