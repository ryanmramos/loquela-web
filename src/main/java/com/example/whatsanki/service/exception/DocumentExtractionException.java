package com.example.whatsanki.service.exception;

import com.example.whatsanki.domain.BackgroundJobFailureKind;

import java.util.Objects;

public class DocumentExtractionException extends RuntimeException {
    private final BackgroundJobFailureKind failureKind;
    private final String errorCode;

    public DocumentExtractionException(
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

    public static DocumentExtractionException permanent(String errorCode) {
        return new DocumentExtractionException(BackgroundJobFailureKind.PERMANENT, errorCode, null);
    }

    public static DocumentExtractionException permanent(String errorCode, Throwable cause) {
        return new DocumentExtractionException(BackgroundJobFailureKind.PERMANENT, errorCode, cause);
    }

    public static DocumentExtractionException transientFailure(String errorCode, Throwable cause) {
        return new DocumentExtractionException(BackgroundJobFailureKind.TRANSIENT, errorCode, cause);
    }

    public BackgroundJobFailureKind failureKind() {
        return failureKind;
    }

    public String errorCode() {
        return errorCode;
    }
}
