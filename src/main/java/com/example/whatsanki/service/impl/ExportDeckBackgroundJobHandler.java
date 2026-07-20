package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.ExportDeckJobPayloadDto;
import com.example.whatsanki.service.BackgroundJobHandler;
import com.example.whatsanki.service.DeckExportService;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.DeckExportException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportDeckBackgroundJobHandler implements BackgroundJobHandler {
    private final DeckExportService deckExportService;

    @Override
    public BackgroundJobEntity.Type supportedType() {
        return BackgroundJobEntity.Type.EXPORT_DECK;
    }

    @Override
    public void handle(ClaimedBackgroundJobDto job) {
        ExportDeckJobPayloadDto payload = parsePayload(job);
        UserId userId = new UserId(job.userId());
        try {
            deckExportService.execute(userId, payload.exportRequestId());
            log.info("Processed EXPORT_DECK job jobId={} userId={} exportRequestId={}",
                    job.jobId().value(), userId.value(), payload.exportRequestId().value());
        } catch (DeckExportException exception) {
            boolean terminal = exception.failureKind() == BackgroundJobFailureKind.PERMANENT
                    || job.attempts() >= job.maxAttempts();
            if (terminal) {
                deckExportService.markFailed(userId, payload.exportRequestId(), exception.errorCode());
            }
            log.warn("Deck export failed jobId={} userId={} exportRequestId={} failureKind={} errorCode={} terminal={}",
                    job.jobId().value(), userId.value(), payload.exportRequestId().value(),
                    exception.failureKind(), exception.errorCode(), terminal);
            throw new BackgroundJobExecutionException(
                    exception.failureKind(), exception.errorCode(), exception
            );
        } catch (RuntimeException exception) {
            if (job.attempts() >= job.maxAttempts()) {
                deckExportService.markFailed(userId, payload.exportRequestId(), "EXPORT_FAILED");
            }
            log.error("Unexpected deck export failure jobId={} userId={} exportRequestId={} exceptionType={}",
                    job.jobId().value(), userId.value(), payload.exportRequestId().value(),
                    exception.getClass().getSimpleName());
            throw new BackgroundJobExecutionException(
                    BackgroundJobFailureKind.TRANSIENT, "EXPORT_FAILED", exception
            );
        }
    }

    private ExportDeckJobPayloadDto parsePayload(ClaimedBackgroundJobDto job) {
        if (job.type() != BackgroundJobEntity.Type.EXPORT_DECK || job.userId() == null) {
            throw invalidPayload(null);
        }
        try {
            return ExportDeckJobPayloadDto.from(job.payload());
        } catch (IllegalArgumentException exception) {
            throw invalidPayload(exception);
        }
    }

    private BackgroundJobExecutionException invalidPayload(Throwable cause) {
        return new BackgroundJobExecutionException(
                BackgroundJobFailureKind.PERMANENT, "INVALID_JOB_PAYLOAD", cause
        );
    }
}
