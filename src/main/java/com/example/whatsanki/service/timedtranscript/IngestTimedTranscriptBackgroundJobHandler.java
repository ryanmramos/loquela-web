package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.TimedTranscriptException;
import com.example.whatsanki.service.job.BackgroundJobHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IngestTimedTranscriptBackgroundJobHandler implements BackgroundJobHandler {
    private static final int SUPPORTED_PAYLOAD_SCHEMA_VERSION = 1;
    private final TimedTranscriptIngestionService ingestionService;

    @Override public BackgroundJobEntity.Type supportedType() { return BackgroundJobEntity.Type.INGEST_TIMED_TRANSCRIPT; }

    @Override
    public void handle(ClaimedBackgroundJobDto job) {
        SourceId sourceId = sourceId(job);
        UserId userId = new UserId(job.userId());
        try {
            ingestionService.ingest(userId, sourceId);
        } catch (TimedTranscriptException exception) {
            boolean terminal = exception.failureKind() == com.example.whatsanki.domain.BackgroundJobFailureKind.PERMANENT
                    || job.attempts() >= job.maxAttempts();
            if (terminal) ingestionService.markFailedAndDelete(userId, sourceId, exception.errorCode());
            throw new BackgroundJobExecutionException(exception.failureKind(), exception.errorCode(), exception);
        } catch (RuntimeException exception) {
            if (job.attempts() >= job.maxAttempts()) {
                ingestionService.markFailedAndDelete(userId, sourceId, "TIMED_TRANSCRIPT_PROCESSING_FAILED");
            }
            throw new BackgroundJobExecutionException(com.example.whatsanki.domain.BackgroundJobFailureKind.TRANSIENT,
                    "TIMED_TRANSCRIPT_PROCESSING_FAILED", exception);
        }
    }

    private SourceId sourceId(ClaimedBackgroundJobDto job) {
        if (job.type() != supportedType() || job.userId() == null) throw invalid();
        Map<String, Object> payload = job.payload();
        if (payload == null
                || !payload.keySet().equals(java.util.Set.of("schemaVersion", "sourceId"))
                || !(payload.get("schemaVersion") instanceof Number version)
                || Double.compare(version.doubleValue(), SUPPORTED_PAYLOAD_SCHEMA_VERSION) != 0) throw invalid();
        try { return new SourceId(UUID.fromString(String.valueOf(payload.get("sourceId")))); }
        catch (RuntimeException exception) { throw invalid(); }
    }

    private BackgroundJobExecutionException invalid() {
        return new BackgroundJobExecutionException(com.example.whatsanki.domain.BackgroundJobFailureKind.PERMANENT,
                "INVALID_JOB_PAYLOAD", null);
    }
}
