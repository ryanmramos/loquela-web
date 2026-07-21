package com.example.whatsanki.dto;

import java.time.Instant;

/** Result of starting account deletion; the code exists only long enough to render TwiML. */
public record AccountDeletionRequestResultDto(String confirmationCode, Instant expiresAt, Outcome outcome) {
    public enum Outcome { CONFIRMATION_REQUIRED, DELETION_IN_PROGRESS }

    public static AccountDeletionRequestResultDto confirmationRequired(String code, Instant expiresAt) {
        return new AccountDeletionRequestResultDto(code, expiresAt, Outcome.CONFIRMATION_REQUIRED);
    }

    public static AccountDeletionRequestResultDto deletionInProgress() {
        return new AccountDeletionRequestResultDto(null, null, Outcome.DELETION_IN_PROGRESS);
    }
}
