package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.SourceDeletionRequestDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.service.SourceManagementService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeleteSourceTwilioCommandHandlerTest {

    private final SourceManagementService sourceManagementService = mock(SourceManagementService.class);
    private final DeleteSourceTwilioCommandHandler handler =
            new DeleteSourceTwilioCommandHandler(sourceManagementService);

    @Test
    void returnsSafeResponsesForEveryDeletionRequestOutcome() {
        TwilioCommandContextDto context = context("source-ref");

        when(sourceManagementService.requestDeletion(context.userId(), "source-ref"))
                .thenReturn(SourceDeletionRequestDto.queued(SourceId.random(), JobId.random()));
        assertThat(handler.handle(context).reply()).contains("deletion queued");

        when(sourceManagementService.requestDeletion(context.userId(), "source-ref"))
                .thenReturn(SourceDeletionRequestDto.alreadyPending(SourceId.random()));
        assertThat(handler.handle(context).reply()).contains("already in progress");

        when(sourceManagementService.requestDeletion(context.userId(), "source-ref"))
                .thenReturn(SourceDeletionRequestDto.notFoundOrDeleted());
        assertThat(handler.handle(context).reply())
                .contains("not found or already deleted")
                .contains("SOURCES");
    }

    private TwilioCommandContextDto context(String reference) {
        return new TwilioCommandContextDto(
                UserId.random(),
                InboundMessageId.random(),
                new ParsedTwilioCommandDto(
                        InboundMessageEntity.CommandType.DELETE_SOURCE,
                        reference,
                        reference,
                        null,
                        null
                )
        );
    }
}
