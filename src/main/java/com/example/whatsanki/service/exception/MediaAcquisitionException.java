package com.example.whatsanki.service.exception;

import com.example.whatsanki.domain.BackgroundJobFailureKind;

import java.util.Objects;

public class MediaAcquisitionException extends RuntimeException {

    private final BackgroundJobFailureKind failureKind;
    private final String errorCode;

    public MediaAcquisitionException(
            BackgroundJobFailureKind failureKind,
            String errorCode,
            Throwable cause
    ) {
        super(errorCode, cause);
        this.failureKind = Objects.requireNonNull(failureKind, "failureKind must not be null");
        if (errorCode == null || !errorCode.matches("[A-Z0-9_]{1,64}")) {
            throw new IllegalArgumentException("errorCode must be a safe machine-readable code");
        }
        this.errorCode = errorCode;
    }

    public static MediaAcquisitionException permanent(String errorCode) {
        return new MediaAcquisitionException(BackgroundJobFailureKind.PERMANENT, errorCode, null);
    }

    public static MediaAcquisitionException permanent(String errorCode, Throwable cause) {
        return new MediaAcquisitionException(BackgroundJobFailureKind.PERMANENT, errorCode, cause);
    }

    public static MediaAcquisitionException transientFailure(String errorCode, Throwable cause) {
        return new MediaAcquisitionException(BackgroundJobFailureKind.TRANSIENT, errorCode, cause);
    }

    public BackgroundJobFailureKind failureKind() {
        return failureKind;
    }

    public String errorCode() {
        return errorCode;
    }
}
