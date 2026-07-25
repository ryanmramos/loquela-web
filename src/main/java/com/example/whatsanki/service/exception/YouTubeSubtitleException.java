package com.example.whatsanki.service.exception;

import com.example.whatsanki.domain.BackgroundJobFailureKind;

import java.util.Objects;

public class YouTubeSubtitleException extends RuntimeException {
    private final BackgroundJobFailureKind failureKind;
    private final String errorCode;

    public YouTubeSubtitleException(BackgroundJobFailureKind failureKind, String errorCode) {
        this(failureKind, errorCode, null);
    }

    public YouTubeSubtitleException(BackgroundJobFailureKind failureKind, String errorCode, Throwable cause) {
        super(errorCode, cause);
        this.failureKind = Objects.requireNonNull(failureKind);
        this.errorCode = Objects.requireNonNull(errorCode);
    }

    public BackgroundJobFailureKind failureKind() { return failureKind; }
    public String errorCode() { return errorCode; }
}
