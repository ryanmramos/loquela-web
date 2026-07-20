package com.example.whatsanki.service.exception;

public class InvalidLookupTermException extends IllegalArgumentException {
    public InvalidLookupTermException(String message) {
        super(message);
    }
}
