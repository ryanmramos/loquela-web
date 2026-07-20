package com.example.whatsanki.service.exception;

import com.example.whatsanki.domain.BackgroundJobFailureKind;

import java.util.Objects;
import java.util.regex.Pattern;

public class OutboundDeliveryException extends RuntimeException {
    private static final Pattern SAFE_ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private final BackgroundJobFailureKind failureKind;
    private final String errorCode;

    public OutboundDeliveryException(
            BackgroundJobFailureKind failureKind,
            String errorCode,
            Throwable cause
    ) {
        super("Outbound delivery failed with safe code " + errorCode, cause);
        this.failureKind = Objects.requireNonNull(failureKind, "failureKind must not be null");
        if (errorCode == null || !SAFE_ERROR_CODE.matcher(errorCode).matches()) {
            throw new IllegalArgumentException("errorCode must be an uppercase safe diagnostic code");
        }
        this.errorCode = errorCode;
    }

    public static OutboundDeliveryException permanent(String errorCode) {
        return new OutboundDeliveryException(BackgroundJobFailureKind.PERMANENT, errorCode, null);
    }

    public static OutboundDeliveryException transientFailure(String errorCode, Throwable cause) {
        return new OutboundDeliveryException(BackgroundJobFailureKind.TRANSIENT, errorCode, cause);
    }

    public BackgroundJobFailureKind failureKind() {
        return failureKind;
    }

    public String errorCode() {
        return errorCode;
    }
}
