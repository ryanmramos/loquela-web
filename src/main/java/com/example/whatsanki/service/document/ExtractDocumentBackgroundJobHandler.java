package com.example.whatsanki.service.document;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.ExtractDocumentJobPayloadDto;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.DocumentExtractionException;
import com.example.whatsanki.service.job.BackgroundJobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtractDocumentBackgroundJobHandler implements BackgroundJobHandler {

    private final DocumentExtractionService documentExtractionService;

    @Override
    public BackgroundJobEntity.Type supportedType() {
        return BackgroundJobEntity.Type.EXTRACT_DOCUMENT;
    }

    @Override
    public void handle(ClaimedBackgroundJobDto job) {
        ExtractDocumentJobPayloadDto payload = parsePayload(job);
        UserId userId = new UserId(job.userId());
        try {
            documentExtractionService.extractAndIndex(userId, payload.sourceId());
            log.info("Processed EXTRACT_DOCUMENT job jobId={} userId={} sourceId={}",
                    job.jobId().value(), userId.value(), payload.sourceId().value());
        } catch (DocumentExtractionException exception) {
            boolean terminal = exception.failureKind() == BackgroundJobFailureKind.PERMANENT
                    || job.attempts() >= job.maxAttempts();
            if (terminal) {
                documentExtractionService.markFailed(userId, payload.sourceId(), exception.errorCode());
            }
            log.warn(
                    "Document extraction failed jobId={} userId={} sourceId={} failureKind={} errorCode={} terminal={}",
                    job.jobId().value(), userId.value(), payload.sourceId().value(),
                    exception.failureKind(), exception.errorCode(), terminal
            );
            throw new BackgroundJobExecutionException(
                    exception.failureKind(), exception.errorCode(), exception
            );
        } catch (RuntimeException exception) {
            if (job.attempts() >= job.maxAttempts()) {
                documentExtractionService.markFailed(userId, payload.sourceId(), "PDF_EXTRACTION_FAILED");
            }
            log.error(
                    "Unexpected PDF extraction failure jobId={} userId={} sourceId={} attempt={} maxAttempts={} exceptionType={}",
                    job.jobId().value(), userId.value(), payload.sourceId().value(),
                    job.attempts(), job.maxAttempts(), exception.getClass().getSimpleName()
            );
            throw new BackgroundJobExecutionException(
                    BackgroundJobFailureKind.TRANSIENT,
                    "PDF_EXTRACTION_FAILED",
                    exception
            );
        }
    }

    private ExtractDocumentJobPayloadDto parsePayload(ClaimedBackgroundJobDto job) {
        if (job.type() != BackgroundJobEntity.Type.EXTRACT_DOCUMENT || job.userId() == null) {
            throw invalidPayload(null);
        }
        try {
            return ExtractDocumentJobPayloadDto.from(job.payload());
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
