package com.example.whatsanki.service.exception;

import com.example.whatsanki.domain.BackgroundJobFailureKind;

public class DeckExportException extends RuntimeException {
    private final BackgroundJobFailureKind failureKind;
    private final String errorCode;

    public DeckExportException(BackgroundJobFailureKind failureKind, String errorCode, Throwable cause) {
        super(errorCode, cause);
        this.failureKind = failureKind;
        this.errorCode = errorCode;
    }

    public static DeckExportException permanent(String errorCode) {
        return new DeckExportException(BackgroundJobFailureKind.PERMANENT, errorCode, null);
    }

    public static DeckExportException transientFailure(String errorCode, Throwable cause) {
        return new DeckExportException(BackgroundJobFailureKind.TRANSIENT, errorCode, cause);
    }

    public BackgroundJobFailureKind failureKind() { return failureKind; }
    public String errorCode() { return errorCode; }
}
