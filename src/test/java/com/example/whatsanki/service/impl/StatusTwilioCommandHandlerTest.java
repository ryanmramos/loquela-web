package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.BackgroundJobStatusDto;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.service.BackgroundJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusTwilioCommandHandlerTest {

    @Mock
    private BackgroundJobService backgroundJobService;

    @InjectMocks
    private StatusTwilioCommandHandler handler;

    @Test
    void returnsEmptyStatusForUserWithoutJobs() {
        TwilioCommandContextDto context = context();
        when(backgroundJobService.findRecent(context.userId(), StatusTwilioCommandHandler.STATUS_LIMIT))
                .thenReturn(List.of());

        assertThat(handler.handle(context).reply()).isEqualTo(StatusTwilioCommandHandler.NO_ACTIVITY_RESPONSE);
        verify(backgroundJobService).findRecent(context.userId(), StatusTwilioCommandHandler.STATUS_LIMIT);
    }

    @Test
    void formatsOnlySafeUserFacingJobMetadata() {
        TwilioCommandContextDto context = context();
        when(backgroundJobService.findRecent(context.userId(), StatusTwilioCommandHandler.STATUS_LIMIT))
                .thenReturn(List.of(
                        status(BackgroundJobEntity.Type.INGEST_TEXT, BackgroundJobEntity.State.RUNNING, 1, 3, null),
                        status(BackgroundJobEntity.Type.LOOKUP, BackgroundJobEntity.State.RETRY_WAIT, 2, 3, "PROVIDER_TIMEOUT"),
                        status(BackgroundJobEntity.Type.EXPORT_DECK, BackgroundJobEntity.State.SUCCEEDED, 1, 3, null),
                        status(BackgroundJobEntity.Type.FETCH_MEDIA, BackgroundJobEntity.State.DEAD, 3, 3, "UNSUPPORTED_MEDIA")
                ));

        assertThat(handler.handle(context).reply())
                .isEqualTo("""
                        Recent activity:
                        1. Text ingestion — running (attempt 1 of 3)
                        2. Dictionary lookup — retrying (attempt 2 of 3)
                        3. Deck export — completed
                        4. Document download — failed (UNSUPPORTED_MEDIA)""")
                .doesNotContain("PROVIDER_TIMEOUT")
                .doesNotContain("{");
    }

    private BackgroundJobStatusDto status(
            BackgroundJobEntity.Type type,
            BackgroundJobEntity.State state,
            int attempts,
            int maxAttempts,
            String errorCode
    ) {
        return new BackgroundJobStatusDto(
                UUID.randomUUID(), type, state, attempts, maxAttempts, errorCode, Instant.now()
        );
    }

    private TwilioCommandContextDto context() {
        return new TwilioCommandContextDto(
                UserId.random(),
                InboundMessageId.random(),
                new ParsedTwilioCommandDto(
                        InboundMessageEntity.CommandType.STATUS,
                        null,
                        null,
                        null,
                        null
                )
        );
    }
}
