package com.example.whatsanki.service;

public class PastedTextLimitExceededException extends RuntimeException {
    private final long maximumCharacters;

    public PastedTextLimitExceededException(long maximumCharacters) {
        super("Pasted text exceeds the configured character limit");
        this.maximumCharacters = maximumCharacters;
    }

    public long maximumCharacters() {
        return maximumCharacters;
    }
}
