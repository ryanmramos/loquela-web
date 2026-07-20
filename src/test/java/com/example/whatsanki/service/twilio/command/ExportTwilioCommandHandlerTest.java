package com.example.whatsanki.service.twilio.command;

import com.example.whatsanki.domain.ExportRequestId;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DeckExportAcceptanceDto;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.service.exception.DeckExportException;
import com.example.whatsanki.service.export.DeckExportService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExportTwilioCommandHandlerTest {
    private final DeckExportService exportService = mock(DeckExportService.class);
    private final ExportTwilioCommandHandler handler = new ExportTwilioCommandHandler(exportService);

    @Test
    void queuesNamedDeckAndAcknowledgesImmediately() {
        TwilioCommandContextDto context = context("Spanish Reading", "spanish reading");
        when(exportService.accept(context.userId(), "Spanish Reading", "spanish reading"))
                .thenReturn(new DeckExportAcceptanceDto(
                        ExportRequestId.random(), JobId.random(), "Spanish Reading", 3
                ));

        assertThat(handler.handle(context).reply())
                .isEqualTo("Export queued for *Spanish Reading* (3 cards). I will send a private download link shortly.");
    }

    @Test
    void givesSpecificGuidanceForEmptyDeck() {
        TwilioCommandContextDto context = context(null, null);
        when(exportService.accept(context.userId(), null, null))
                .thenThrow(DeckExportException.permanent("DECK_EMPTY"));

        assertThat(handler.handle(context).reply()).contains("no cards").contains("*SAVE*");
    }

    private TwilioCommandContextDto context(String argument, String normalized) {
        return new TwilioCommandContextDto(
                UserId.random(),
                InboundMessageId.random(),
                new ParsedTwilioCommandDto(
                        InboundMessageEntity.CommandType.EXPORT, argument, normalized, null, null
                )
        );
    }
}
