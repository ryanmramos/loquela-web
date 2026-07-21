package com.example.whatsanki.dto;

import com.example.whatsanki.domain.JobId;

public record AccountDeletionConfirmationResultDto(Outcome outcome, JobId jobId) {
    public enum Outcome { QUEUED, INVALID_OR_EXPIRED, DELETION_IN_PROGRESS }

    public static AccountDeletionConfirmationResultDto queued(JobId jobId) {
        return new AccountDeletionConfirmationResultDto(Outcome.QUEUED, jobId);
    }

    public static AccountDeletionConfirmationResultDto invalidOrExpired() {
        return new AccountDeletionConfirmationResultDto(Outcome.INVALID_OR_EXPIRED, null);
    }

    public static AccountDeletionConfirmationResultDto deletionInProgress() {
        return new AccountDeletionConfirmationResultDto(Outcome.DELETION_IN_PROGRESS, null);
    }
}
