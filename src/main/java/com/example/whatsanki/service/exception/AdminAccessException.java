package com.example.whatsanki.service.exception;

import org.springframework.http.HttpStatus;

public final class AdminAccessException extends RuntimeException {
    private final HttpStatus status;
    private final String problemCode;

    public AdminAccessException(HttpStatus status, String problemCode) {
        super("Administrative access operation was rejected");
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

