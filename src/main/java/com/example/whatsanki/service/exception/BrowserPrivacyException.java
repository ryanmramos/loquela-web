package com.example.whatsanki.service.exception;

import org.springframework.http.HttpStatus;

public final class BrowserPrivacyException extends RuntimeException {
    private final HttpStatus status;
    private final String problemCode;

    public BrowserPrivacyException(HttpStatus status, String problemCode) {
        super("Browser privacy operation was rejected");
        this.status = status;
        this.problemCode = problemCode;
    }

    public HttpStatus status() {
        return status;
    }

    public String problemCode() {
        return problemCode;
    }
}
