package com.example.whatsanki.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/** Adds a bounded correlation ID to logs and responses for one HTTP request. */
@Slf4j
@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String CORRELATION_HEADER = "X-Correlation-ID";
    public static final String CORRELATION_MDC_KEY = "correlationId";

    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = correlationId(request.getHeader(CORRELATION_HEADER));
        long startedAt = System.nanoTime();

        MDC.put(CORRELATION_MDC_KEY, correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);
        try {
            log.info("HTTP request started method={} path={}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info(
                    "HTTP request completed method={} path={} status={} durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs
            );
            MDC.remove(CORRELATION_MDC_KEY);
        }
    }

    private String correlationId(String suppliedId) {
        // Do not echo arbitrary header text into logs or response headers.
        if (suppliedId != null && SAFE_CORRELATION_ID.matcher(suppliedId).matches()) {
            return suppliedId;
        }
        return UUID.randomUUID().toString();
    }
}
