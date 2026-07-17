package com.example.whatsanki.service.exception;

import java.util.Objects;
import java.util.regex.Pattern;

public class PastedTextIngestionException extends RuntimeException {
    private static final Pattern SAFE_ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private final String errorCode;

    public PastedTextIngestionException(String errorCode) {
        this(errorCode, null);
    }

    public PastedTextIngestionException(String errorCode, Throwable cause) {
        super("Pasted-text ingestion failed with safe code " + errorCode, cause);
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        if (!SAFE_ERROR_CODE.matcher(errorCode).matches()) {
            throw new IllegalArgumentException("errorCode must be an uppercase safe diagnostic code");
        }
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
