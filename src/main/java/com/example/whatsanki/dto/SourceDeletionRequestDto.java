package com.example.whatsanki.dto;

import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceId;

public record SourceDeletionRequestDto(
        Outcome outcome,
        SourceId sourceId,
        JobId jobId
) {
    public enum Outcome {
        QUEUED,
        ALREADY_PENDING,
        NOT_FOUND_OR_DELETED
    }

    public static SourceDeletionRequestDto queued(SourceId sourceId, JobId jobId) {
        return new SourceDeletionRequestDto(Outcome.QUEUED, sourceId, jobId);
    }

    public static SourceDeletionRequestDto alreadyPending(SourceId sourceId) {
        return new SourceDeletionRequestDto(Outcome.ALREADY_PENDING, sourceId, null);
    }

    public static SourceDeletionRequestDto notFoundOrDeleted() {
        return new SourceDeletionRequestDto(Outcome.NOT_FOUND_OR_DELETED, null, null);
    }
}
