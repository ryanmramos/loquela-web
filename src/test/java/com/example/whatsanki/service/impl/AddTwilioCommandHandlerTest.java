package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.PastedTextAcceptanceDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.service.PastedTextIngestionService;
import com.example.whatsanki.service.PastedTextLimitExceededException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddTwilioCommandHandlerTest {

    @Mock
    private PastedTextIngestionService pastedTextIngestionService;

    @InjectMocks
    private AddTwilioCommandHandler handler;

    @Test
    void registersAddAndReturnsAcceptedResponse() {
        TwilioCommandContextDto context = context("El árbol está aquí.");
        SourceId sourceId = SourceId.random();
        when(pastedTextIngestionService.accept(
                context.userId(), context.inboundMessageId(), context.command().argument()
        )).thenReturn(new PastedTextAcceptanceDto(sourceId, 19));

        assertThat(handler.supportedCommand()).isEqualTo(InboundMessageEntity.CommandType.ADD);
        assertThat(handler.handle(context).reply()).isEqualTo(AddTwilioCommandHandler.ACCEPTED_RESPONSE);
        verify(pastedTextIngestionService).accept(
                context.userId(), context.inboundMessageId(), "El árbol está aquí."
        );
    }

    @Test
    void returnsSafeGuidanceWhenTextExceedsConfiguredLimit() {
        TwilioCommandContextDto context = context("too long");
        when(pastedTextIngestionService.accept(
                context.userId(), context.inboundMessageId(), context.command().argument()
        )).thenThrow(new PastedTextLimitExceededException(5));

        assertThat(handler.handle(context).reply())
                .isEqualTo("Text is too long. Send at most 5 characters with ADD.");
    }

    private TwilioCommandContextDto context(String text) {
        return new TwilioCommandContextDto(
                UserId.random(),
                InboundMessageId.random(),
                new ParsedTwilioCommandDto(
                        InboundMessageEntity.CommandType.ADD,
                        text,
                        text.toLowerCase(),
                        null,
                        null
                )
        );
    }
}
