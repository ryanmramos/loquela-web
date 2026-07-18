package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DocumentAcceptanceDto;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioMediaMetadataDto;
import com.example.whatsanki.service.DocumentIngestionService;
import com.example.whatsanki.service.exception.MediaAcquisitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentTwilioCommandHandlerTest {

    private final DocumentIngestionService ingestionService = mock(DocumentIngestionService.class);
    private final DocumentTwilioCommandHandler handler = new DocumentTwilioCommandHandler(ingestionService);

    @Test
    void registersDocumentAndAcknowledgesAcceptedMedia() {
        TwilioCommandContextDto context = context();
        when(ingestionService.accept(context.userId(), context.inboundMessageId(), context.command().media()))
                .thenReturn(new DocumentAcceptanceDto(SourceId.random()));

        assertThat(handler.supportedCommand()).isEqualTo(InboundMessageEntity.CommandType.DOCUMENT);
        assertThat(handler.handle(context).reply()).isEqualTo(DocumentTwilioCommandHandler.ACCEPTED_RESPONSE);
        verify(ingestionService).accept(context.userId(), context.inboundMessageId(), context.command().media());
    }

    @Test
    void returnsSafeGuidanceForUnsupportedMedia() {
        TwilioCommandContextDto context = context();
        when(ingestionService.accept(context.userId(), context.inboundMessageId(), context.command().media()))
                .thenThrow(MediaAcquisitionException.permanent("UNSUPPORTED_MEDIA_TYPE"));

        assertThat(handler.handle(context).reply()).isEqualTo(DocumentTwilioCommandHandler.UNSUPPORTED_RESPONSE);
    }

    private TwilioCommandContextDto context() {
        var media = new TwilioMediaMetadataDto(1, "https://api.twilio.com/media", "text/plain", null);
        return new TwilioCommandContextDto(
                UserId.random(),
                InboundMessageId.random(),
                new ParsedTwilioCommandDto(
                        InboundMessageEntity.CommandType.DOCUMENT, null, "text/plain", media, null
                )
        );
    }
}
