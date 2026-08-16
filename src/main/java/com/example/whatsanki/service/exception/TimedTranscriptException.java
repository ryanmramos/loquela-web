package com.example.whatsanki.service.exception;

import com.example.whatsanki.domain.BackgroundJobFailureKind;

public class TimedTranscriptException extends RuntimeException {
    private final BackgroundJobFailureKind failureKind;
    private final String errorCode;

    private TimedTranscriptException(BackgroundJobFailureKind failureKind, String errorCode, Throwable cause) {
        super(errorCode, cause);
        this.failureKind = failureKind;
        this.errorCode = errorCode;
    }

    public static TimedTranscriptException permanent(String errorCode) {
        return new TimedTranscriptException(BackgroundJobFailureKind.PERMANENT, errorCode, null);
    }

    public static TimedTranscriptException transientFailure(String errorCode, Throwable cause) {
        return new TimedTranscriptException(BackgroundJobFailureKind.TRANSIENT, errorCode, cause);
    }

    public BackgroundJobFailureKind failureKind() { return failureKind; }
    public String errorCode() { return errorCode; }
}
