package com.example.whatsanki.service.exception;

import java.util.Objects;

/**
 * Provider-neutral failure raised by dictionary adapters after a supported request
 * reaches an external dependency. Lookup orchestration maps this classification to
 * stable job retry behavior without depending on vendor exception types.
 */
public class DictionaryProviderException extends RuntimeException {

    public enum FailureKind {
        TIMEOUT,
        RATE_LIMITED,
        TEMPORARY,
        PERMANENT
    }

    private final String providerId;
    private final FailureKind failureKind;

    public DictionaryProviderException(
            String providerId,
            FailureKind failureKind,
            Throwable cause
    ) {
        super(safeMessage(providerId, failureKind), cause);
        this.providerId = providerId;
        this.failureKind = Objects.requireNonNull(failureKind, "failureKind must not be null");
    }

    public static DictionaryProviderException timeout(String providerId, Throwable cause) {
        return new DictionaryProviderException(providerId, FailureKind.TIMEOUT, cause);
    }

    public static DictionaryProviderException rateLimited(String providerId, Throwable cause) {
        return new DictionaryProviderException(providerId, FailureKind.RATE_LIMITED, cause);
    }

    public static DictionaryProviderException temporary(String providerId, Throwable cause) {
        return new DictionaryProviderException(providerId, FailureKind.TEMPORARY, cause);
    }

    public static DictionaryProviderException permanent(String providerId, Throwable cause) {
        return new DictionaryProviderException(providerId, FailureKind.PERMANENT, cause);
    }

    public String providerId() {
        return providerId;
    }

    public FailureKind failureKind() {
        return failureKind;
    }

    private static String safeMessage(String providerId, FailureKind failureKind) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        return "Dictionary provider " + providerId + " failed with classification "
                + Objects.requireNonNull(failureKind, "failureKind must not be null");
    }
}
