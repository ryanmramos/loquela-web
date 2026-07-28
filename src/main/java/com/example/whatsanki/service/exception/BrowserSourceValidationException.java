package com.example.whatsanki.service.exception;

import org.springframework.http.HttpStatus;

public class BrowserSourceValidationException extends RuntimeException {
    private final String problemCode;
    private final HttpStatus status;

    public BrowserSourceValidationException(String problemCode, HttpStatus status) {
        this.problemCode = problemCode;
        this.status = status;
    }

    public String problemCode() {
        return problemCode;
    }

    public HttpStatus status() {
        return status;
    }
}
