package com.example.whatsanki.service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BrowserPracticeException extends RuntimeException {
    private final HttpStatus status;
    private final String problemCode;

    public BrowserPracticeException(HttpStatus status, String problemCode) {
        super(problemCode);
        this.status = status;
        this.problemCode = problemCode;
    }
}
