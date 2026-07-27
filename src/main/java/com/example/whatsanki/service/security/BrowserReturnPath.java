package com.example.whatsanki.service.security;

import java.net.URI;

/**
 * Restricts authentication redirects to routes owned by the authenticated SPA.
 */
public final class BrowserReturnPath {

    public static final String SESSION_ATTRIBUTE = BrowserReturnPath.class.getName() + ".returnPath";
    public static final String DEFAULT_PATH = "/app";

    private BrowserReturnPath() {
    }

    public static String validate(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return DEFAULT_PATH;
        }

        String value = candidate.trim();
        if (!value.startsWith("/app")
                || value.startsWith("//")
                || value.contains("\\")
                || value.contains("\r")
                || value.contains("\n")) {
            return DEFAULT_PATH;
        }

        URI parsed;
        try {
            parsed = URI.create(value);
        } catch (IllegalArgumentException exception) {
            return DEFAULT_PATH;
        }
        return parsed.isAbsolute() || parsed.getHost() != null ? DEFAULT_PATH : value;
    }
}
