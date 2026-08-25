package com.example.whatsanki.service;

import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.AccountDeletionConfirmationResultDto;
import com.example.whatsanki.dto.AccountDeletionRequestResultDto;

/** Coordinates the two-step account-deletion request and its destructive worker. */
public interface AccountDeletionService {
    AccountDeletionRequestResultDto request(UserId userId);

    AccountDeletionConfirmationResultDto confirm(UserId userId, String confirmationCode);

    AccountDeletionConfirmationResultDto confirmBrowser(UserId userId);

    boolean isDeletionInProgress(UserId userId);

    /** Deletes user-owned objects first, then removes the database rows in one transaction. */
    void delete(UserId userId, JobId deletionJobId);
}
