package com.example.whatsanki.domain;

public record ProviderMessageId(String value) {
    public ProviderMessageId {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("Provider message ID must contain 1 to 128 characters");
        }
        value = value.strip();
    }
}
