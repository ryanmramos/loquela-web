package com.example.whatsanki.service.exception;

import org.springframework.http.HttpStatus;

public final class BrowserActivityException extends RuntimeException {
    private final HttpStatus status;
    private final String problemCode;

    public BrowserActivityException(HttpStatus status, String problemCode) {
        super("Browser activity request failed");
        this.status = status;
        this.problemCode = problemCode;
    }

    public HttpStatus status() { return status; }
    public String problemCode() { return problemCode; }
}
