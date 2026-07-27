package com.example.whatsanki.service.exception;

public final class BrowserAccountUnavailableException extends RuntimeException {

    private final String problemCode;

    public BrowserAccountUnavailableException(String problemCode) {
        super("Browser account is unavailable");
        this.problemCode = problemCode;
    }

    public String problemCode() {
        return problemCode;
    }
}
