package com.example.whatsanki.service.exception;

import com.example.whatsanki.domain.BackgroundJobFailureKind;

import java.util.Objects;

public class TwilioMediaCleanupException extends RuntimeException {
    private final BackgroundJobFailureKind failureKind;
    private final String errorCode;

    public TwilioMediaCleanupException(
            BackgroundJobFailureKind failureKind,
            String errorCode,
            Throwable cause
    ) {
        super(errorCode, cause);
        this.failureKind = Objects.requireNonNull(failureKind, "failureKind must not be null");
        if (errorCode == null || !errorCode.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("errorCode must be a safe machine-readable code");
        }
        this.errorCode = errorCode;
    }

    public static TwilioMediaCleanupException permanent(String errorCode) {
        return new TwilioMediaCleanupException(BackgroundJobFailureKind.PERMANENT, errorCode, null);
    }

    public static TwilioMediaCleanupException transientFailure(String errorCode, Throwable cause) {
        return new TwilioMediaCleanupException(BackgroundJobFailureKind.TRANSIENT, errorCode, cause);
    }

    public BackgroundJobFailureKind failureKind() {
        return failureKind;
    }

    public String errorCode() {
        return errorCode;
    }
}
