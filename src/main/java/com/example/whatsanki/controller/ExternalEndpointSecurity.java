package com.example.whatsanki.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Reviewable classification for controllers that expose non-GET operations outside the process.
 * The inventory test makes adding an unclassified mutation a build failure.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExternalEndpointSecurity {
    Control value();

    enum Control {
        BROWSER_SESSION_CSRF,
        BROWSER_SESSION_CSRF_OWNER_SCOPED,
        BROWSER_SESSION_CSRF_ADMIN,
        ANONYMOUS_DEMO_SESSION_CSRF,
        DEVELOPMENT_ONLY_CSRF,
        TWILIO_SIGNED_WEBHOOK
    }
}
