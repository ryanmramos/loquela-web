package com.example.whatsanki.service.twilio;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.TwilioCommandHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TwilioCommandServiceImplTest {

    @Test
    void dispatchesToHandlerRegisteredForCommandType() {
        TwilioCommandHandler handler = mock(TwilioCommandHandler.class);
        TwilioCommandContextDto context = context(InboundMessageEntity.CommandType.STATUS, null);
        TwilioWebhookResponseDto expected = new TwilioWebhookResponseDto("status");
        when(handler.supportedCommand()).thenReturn(InboundMessageEntity.CommandType.STATUS);
        when(handler.handle(context)).thenReturn(expected);
        TwilioCommandServiceImpl service = new TwilioCommandServiceImpl(List.of(handler));

        assertThat(service.handle(context)).isSameAs(expected);
        verify(handler).handle(context);
    }

    @Test
    void returnsParserValidationErrorBeforeDispatch() {
        TwilioCommandHandler handler = mock(TwilioCommandHandler.class);
        when(handler.supportedCommand()).thenReturn(InboundMessageEntity.CommandType.STATUS);
        TwilioCommandServiceImpl service = new TwilioCommandServiceImpl(List.of(handler));
        TwilioCommandContextDto context = context(
                InboundMessageEntity.CommandType.STATUS,
                "This command does not accept additional text."
        );

        assertThat(service.handle(context).reply())
                .isEqualTo("This command does not accept additional text.");
        verify(handler, never()).handle(context);
    }

    @Test
    void returnsGuidanceForUnknownAndMilestoneMessageForUnsupportedCommand() {
        TwilioCommandServiceImpl service = new TwilioCommandServiceImpl(List.of());

        assertThat(service.handle(context(InboundMessageEntity.CommandType.UNKNOWN, null)).reply())
                .isEqualTo(TwilioCommandServiceImpl.UNKNOWN_COMMAND_RESPONSE);
        assertThat(service.handle(context(InboundMessageEntity.CommandType.ADD, null)).reply())
                .isEqualTo(TwilioCommandServiceImpl.NOT_IMPLEMENTED_RESPONSE);
    }

    @Test
    void rejectsDuplicateHandlerRegistrationAtStartup() {
        TwilioCommandHandler first = mock(TwilioCommandHandler.class);
        TwilioCommandHandler second = mock(TwilioCommandHandler.class);
        when(first.supportedCommand()).thenReturn(InboundMessageEntity.CommandType.STATUS);
        when(second.supportedCommand()).thenReturn(InboundMessageEntity.CommandType.STATUS);

        assertThatThrownBy(() -> new TwilioCommandServiceImpl(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STATUS");
    }

    private TwilioCommandContextDto context(
            InboundMessageEntity.CommandType type,
            String validationError
    ) {
        ParsedTwilioCommandDto command = new ParsedTwilioCommandDto(
                type,
                null,
                null,
                null,
                validationError
        );
        return new TwilioCommandContextDto(UserId.random(), InboundMessageId.random(), command);
    }
}
