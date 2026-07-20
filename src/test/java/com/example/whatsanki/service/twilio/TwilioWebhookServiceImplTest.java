package com.example.whatsanki.service.twilio;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookRequestDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.service.UserIdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwilioWebhookServiceImplTest {

    @Mock
    private InboundMessageRepository inboundMessageRepository;

    @Mock
    private UserIdentityService userIdentityService;

    @Mock
    private TwilioCommandParser twilioCommandParser;

    @Mock
    private TwilioCommandService twilioCommandService;

    private TwilioWebhookServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TwilioWebhookServiceImpl(
                inboundMessageRepository,
                userIdentityService,
                twilioCommandParser,
                twilioCommandService,
                new WhatsAnkiConfigurationDto()
        );
    }

    @Test
    void skipsIdentityResolutionAndHandlingForPreviouslyRecordedSid() {
        TwilioWebhookRequestDto request = request();
        when(inboundMessageRepository.findByTwilioMessageSid(request.getMessageSid()))
                .thenReturn(Optional.of(new InboundMessageEntity()));

        TwilioWebhookResponseDto response = service.handle(request);

        assertThat(response.reply()).isEqualTo(TwilioWebhookServiceImpl.DUPLICATE_MESSAGE_RESPONSE);
        verify(userIdentityService, never()).resolveOrCreate(any());
        verify(twilioCommandParser, never()).parse(any());
        verify(twilioCommandService, never()).handle(any());
    }

    @Test
    void resolvesUserRecordsMessageAndHandlesCommand() {
        TwilioWebhookRequestDto request = request();
        UserId userId = UserId.random();
        ParsedTwilioCommandDto command = command(InboundMessageEntity.CommandType.HELP);
        TwilioWebhookResponseDto expected = new TwilioWebhookResponseDto("help response");
        when(inboundMessageRepository.findByTwilioMessageSid(request.getMessageSid()))
                .thenReturn(Optional.empty());
        when(userIdentityService.resolveOrCreate(request.getFrom())).thenReturn(userId);
        when(twilioCommandParser.parse(request)).thenReturn(command);
        when(inboundMessageRepository.insertIfAbsent(
                any(UUID.class), eq(request.getMessageSid()), eq(userId.value()), eq("HELP"),
                any(Instant.class), any(Instant.class)
        )).thenReturn(1);
        when(twilioCommandService.handle(any(TwilioCommandContextDto.class))).thenReturn(expected);

        TwilioWebhookResponseDto response = service.handle(request);

        assertThat(response).isSameAs(expected);
        ArgumentCaptor<UUID> inboundMessageIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(inboundMessageRepository).insertIfAbsent(
                inboundMessageIdCaptor.capture(), eq(request.getMessageSid()), eq(userId.value()), eq("HELP"),
                any(Instant.class), any(Instant.class)
        );
        ArgumentCaptor<TwilioCommandContextDto> contextCaptor =
                ArgumentCaptor.forClass(TwilioCommandContextDto.class);
        verify(twilioCommandService).handle(contextCaptor.capture());
        assertThat(contextCaptor.getValue().userId()).isEqualTo(userId);
        assertThat(contextCaptor.getValue().inboundMessageId().value())
                .isEqualTo(inboundMessageIdCaptor.getValue());
        assertThat(contextCaptor.getValue().command()).isSameAs(command);
    }

    @Test
    void doesNotHandleCommandWhenConcurrentRequestWinsSidInsert() {
        TwilioWebhookRequestDto request = request();
        UserId userId = UserId.random();
        ParsedTwilioCommandDto command = command(InboundMessageEntity.CommandType.HELP);
        when(inboundMessageRepository.findByTwilioMessageSid(request.getMessageSid()))
                .thenReturn(Optional.empty());
        when(userIdentityService.resolveOrCreate(request.getFrom())).thenReturn(userId);
        when(twilioCommandParser.parse(request)).thenReturn(command);
        when(inboundMessageRepository.insertIfAbsent(
                any(UUID.class), eq(request.getMessageSid()), eq(userId.value()), eq("HELP"),
                any(Instant.class), any(Instant.class)
        )).thenReturn(0);

        TwilioWebhookResponseDto response = service.handle(request);

        assertThat(response.reply()).isEqualTo(TwilioWebhookServiceImpl.DUPLICATE_MESSAGE_RESPONSE);
        verify(twilioCommandService, never()).handle(any());
    }

    private ParsedTwilioCommandDto command(InboundMessageEntity.CommandType type) {
        return new ParsedTwilioCommandDto(type, null, null, null, null);
    }

    private TwilioWebhookRequestDto request() {
        TwilioWebhookRequestDto request = new TwilioWebhookRequestDto();
        request.setMessageSid("SM00000000000000000000000000000001");
        request.setFrom("whatsapp:+15550000001");
        request.setTo("whatsapp:+15550000002");
        request.setBody("HELP");
        return request;
    }
}
