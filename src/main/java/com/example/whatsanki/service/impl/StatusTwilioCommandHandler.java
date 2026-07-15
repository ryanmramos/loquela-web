package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.BackgroundJobStatusDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.BackgroundJobService;
import com.example.whatsanki.service.TwilioCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatusTwilioCommandHandler implements TwilioCommandHandler {

    static final int STATUS_LIMIT = 10;
    static final String NO_ACTIVITY_RESPONSE = "No recent or active jobs.";

    private final BackgroundJobService backgroundJobService;

    @Override
    public InboundMessageEntity.CommandType supportedCommand() {
        return InboundMessageEntity.CommandType.STATUS;
    }

    @Override
    @Transactional(readOnly = true)
    public TwilioWebhookResponseDto handle(TwilioCommandContextDto context) {
        List<BackgroundJobStatusDto> jobs = backgroundJobService.findRecent(context.userId(), STATUS_LIMIT);
        log.info("Processed STATUS command userId={} resultCount={}", context.userId().value(), jobs.size());
        if (jobs.isEmpty()) {
            return new TwilioWebhookResponseDto(NO_ACTIVITY_RESPONSE);
        }

        // STATUS intentionally formats only controlled metadata. Payloads, lease owners,
        // internal IDs, and exception messages must never be returned through WhatsApp.
        StringBuilder response = new StringBuilder("Recent activity:\n");
        for (int index = 0; index < jobs.size(); index++) {
            BackgroundJobStatusDto job = jobs.get(index);
            response.append(index + 1)
                    .append(". ")
                    .append(label(job.type()))
                    .append(" — ")
                    .append(state(job));
            if (index < jobs.size() - 1) {
                response.append('\n');
            }
        }
        return new TwilioWebhookResponseDto(response.toString());
    }

    private String label(BackgroundJobEntity.Type type) {
        return switch (type) {
            case FETCH_MEDIA -> "Document download";
            case INGEST_TEXT -> "Text ingestion";
            case LOOKUP -> "Dictionary lookup";
            case EXPORT_DECK -> "Deck export";
            case DELETE_SOURCE -> "Source deletion";
            case DELETE_USER_DATA -> "Account deletion";
        };
    }

    private String state(BackgroundJobStatusDto job) {
        return switch (job.state()) {
            case QUEUED -> "queued";
            case RUNNING -> "running (attempt " + job.attempts() + " of " + job.maxAttempts() + ")";
            case RETRY_WAIT -> "retrying (attempt " + job.attempts() + " of " + job.maxAttempts() + ")";
            case SUCCEEDED -> "completed";
            case DEAD -> job.lastErrorCode() == null
                    ? "failed"
                    : "failed (" + job.lastErrorCode() + ")";
            case CANCELLED -> "cancelled";
        };
    }
}
