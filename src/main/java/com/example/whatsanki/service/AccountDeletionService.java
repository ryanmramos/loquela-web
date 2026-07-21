package com.example.whatsanki.service;

import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.AccountDeletionConfirmationResultDto;
import com.example.whatsanki.dto.AccountDeletionRequestResultDto;

public interface AccountDeletionService {
    AccountDeletionRequestResultDto request(UserId userId);

    AccountDeletionConfirmationResultDto confirm(UserId userId, String confirmationCode);

    boolean isDeletionInProgress(UserId userId);

    void delete(UserId userId, JobId deletionJobId);
}
