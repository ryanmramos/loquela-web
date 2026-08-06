package com.example.whatsanki.service.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;
import java.net.URI;

public final class BrowserAuthenticationFailureHandler implements AuthenticationFailureHandler {
    private final URI frontendBaseUrl;

    public BrowserAuthenticationFailureHandler(URI frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        String reason = exception instanceof OAuth2AuthenticationException oauth
                && "access_denied".equals(oauth.getError().getErrorCode())
                ? "access-denied"
                : "oauth-error";
        response.sendRedirect(BrowserAuthenticationSuccessHandler.frontendUrl(
                frontendBaseUrl,
                "/login?reason=" + reason
        ));
    }
}

