package com.example.whatsanki.service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class DemoException extends RuntimeException {
    private final HttpStatus status;
    private final String problemCode;
    private final Integer retryAfterSeconds;

    public DemoException(HttpStatus status, String problemCode) {
        this(status, problemCode, null, null);
    }

    public DemoException(HttpStatus status, String problemCode, Integer retryAfterSeconds, Throwable cause) {
        super(problemCode, cause);
        this.status = status;
        this.problemCode = problemCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
