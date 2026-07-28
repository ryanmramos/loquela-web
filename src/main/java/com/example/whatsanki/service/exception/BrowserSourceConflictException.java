package com.example.whatsanki.service.exception;

public class BrowserSourceConflictException extends RuntimeException {
    private final String problemCode;

    public BrowserSourceConflictException(String problemCode) {
        this.problemCode = problemCode;
    }

    public String problemCode() {
        return problemCode;
    }
}
