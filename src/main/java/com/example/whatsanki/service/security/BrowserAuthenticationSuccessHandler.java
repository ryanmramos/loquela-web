package com.example.whatsanki.service.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.net.URI;

public final class BrowserAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final URI frontendBaseUrl;

    public BrowserAuthenticationSuccessHandler(URI frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        HttpSession session = request.getSession(false);
        Object requested = session == null
                ? null
                : session.getAttribute(BrowserReturnPath.SESSION_ATTRIBUTE);
        if (session != null) {
            session.removeAttribute(BrowserReturnPath.SESSION_ATTRIBUTE);
        }
        String returnPath = BrowserReturnPath.validate(requested instanceof String path ? path : null);
        response.sendRedirect(frontendUrl(frontendBaseUrl, returnPath));
    }

    public static String frontendUrl(URI baseUrl, String path) {
        String origin = baseUrl.getScheme() + "://" + baseUrl.getAuthority();
        return origin + path;
    }
}
