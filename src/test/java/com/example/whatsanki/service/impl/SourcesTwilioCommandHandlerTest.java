package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.SourceSummaryDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.service.SourceManagementService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourcesTwilioCommandHandlerTest {

    private final SourceManagementService sourceManagementService = mock(SourceManagementService.class);
    private final SourcesTwilioCommandHandler handler = new SourcesTwilioCommandHandler(sourceManagementService);

    @Test
    void returnsGuidanceWhenTheUserHasNoSources() {
        TwilioCommandContextDto context = context();
        when(sourceManagementService.listRecent(context.userId(), 10)).thenReturn(List.of());

        assertThat(handler.handle(context).reply())
                .isEqualTo(SourcesTwilioCommandHandler.NO_SOURCES_RESPONSE);
    }

    @Test
    void formatsReadyFailedProcessingAndDeletingStatesWithoutMultilineNames() {
        TwilioCommandContextDto context = context();
        when(sourceManagementService.listRecent(context.userId(), 10)).thenReturn(List.of(
                summary("ready-id", SourceDocumentEntity.Status.READY, "Novel\nchapter", 2, null),
                summary("failed-id", SourceDocumentEntity.Status.FAILED, "Import", null, "EMPTY_TEXT"),
                summary("working-id", SourceDocumentEntity.Status.INDEXING, "Notes", null, null),
                summary("deleting-id", SourceDocumentEntity.Status.DELETING, "Old source", null, null)
        ));

        String response = handler.handle(context).reply();

        assertThat(response)
                .contains("ready-id — Novel chapter — ready (2 passages)")
                .contains("failed-id — Import — failed (EMPTY_TEXT)")
                .contains("working-id — Notes — processing")
                .contains("deleting-id — Old source — deleting")
                .endsWith("Delete one with *DELETE SOURCE* <id>.");
    }

    private TwilioCommandContextDto context() {
        return new TwilioCommandContextDto(
                UserId.random(),
                InboundMessageId.random(),
                new ParsedTwilioCommandDto(InboundMessageEntity.CommandType.SOURCES, null, null, null, null)
        );
    }

    private SourceSummaryDto summary(
            String reference,
            SourceDocumentEntity.Status status,
            String name,
            Integer passages,
            String errorCode
    ) {
        return new SourceSummaryDto(
                SourceId.random(),
                reference,
                SourceDocumentEntity.Kind.PASTED_TEXT,
                status,
                name,
                20L,
                passages,
                errorCode,
                Instant.now()
        );
    }
}
