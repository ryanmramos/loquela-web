package com.example.whatsanki.service.exception;

import org.springframework.http.HttpStatus;

public class BrowserDeckException extends RuntimeException {
    private final HttpStatus status;
    private final String problemCode;

    public BrowserDeckException(HttpStatus status, String problemCode) {
        this.status = status;
        this.problemCode = problemCode;
    }

    public HttpStatus status() { return status; }
    public String problemCode() { return problemCode; }
}
