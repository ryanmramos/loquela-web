package com.example.whatsanki.service.security;

import com.example.whatsanki.dto.ApiProblemDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class AuthenticationRateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final int authLimit;
    private final int linkLimit;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public AuthenticationRateLimitFilter(ObjectMapper objectMapper, int authLimit, int linkLimit) {
        this(objectMapper, authLimit, linkLimit, Clock.systemUTC());
    }

    AuthenticationRateLimitFilter(ObjectMapper objectMapper, int authLimit, int linkLimit, Clock clock) {
        this.objectMapper = objectMapper;
        this.authLimit = authLimit;
        this.linkLimit = linkLimit;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/oauth2/authorization/")
                && !path.startsWith("/login/oauth2/code/")
                && !path.startsWith("/api/v1/auth/")
                && !path.startsWith("/api/v1/whatsapp-link-challenges");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        int limit = request.getRequestURI().startsWith("/api/v1/whatsapp-link-challenges")
                ? linkLimit : authLimit;
        Instant minute = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        String key = request.getRemoteAddr() + "|" + request.getRequestURI() + "|" + minute;
        Window window = windows.computeIfAbsent(key, ignored -> new Window(minute));
        if (window.count().incrementAndGet() > limit) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("Retry-After", "60");
            objectMapper.writeValue(
                    response.getOutputStream(),
                    new ApiProblemDto(
                            URI.create("https://loquela.app/problems/rate-limit"),
                            "Too many requests",
                            429,
                            "AUTH_RATE_LIMITED",
                            "Too many authentication or account-linking requests.",
                            request.getRequestURI(),
                            MDC.get("correlationId"),
                            List.of()
                    )
            );
            return;
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> entry.getValue().minute().isBefore(minute));
        }
        filterChain.doFilter(request, response);
    }

    private record Window(Instant minute, AtomicInteger count) {
        private Window(Instant minute) {
            this(minute, new AtomicInteger());
        }
    }
}
