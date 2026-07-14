package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.TwilioWebhookRequestDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.service.TwilioCommandService;
import com.example.whatsanki.service.UserIdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private TwilioCommandService twilioCommandService;

    private TwilioWebhookServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TwilioWebhookServiceImpl(
                inboundMessageRepository,
                userIdentityService,
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
        verify(twilioCommandService, never()).classify(any());
        verify(twilioCommandService, never()).handle(any());
    }

    @Test
    void resolvesUserRecordsMessageAndHandlesCommand() {
        TwilioWebhookRequestDto request = request();
        UserId userId = UserId.random();
        TwilioWebhookResponseDto expected = new TwilioWebhookResponseDto("help response");
        when(inboundMessageRepository.findByTwilioMessageSid(request.getMessageSid()))
                .thenReturn(Optional.empty());
        when(userIdentityService.resolveOrCreate(request.getFrom())).thenReturn(userId);
        when(twilioCommandService.classify(request)).thenReturn(InboundMessageEntity.CommandType.HELP);
        when(inboundMessageRepository.insertIfAbsent(
                any(UUID.class), eq(request.getMessageSid()), eq(userId.value()), eq("HELP"),
                any(Instant.class), any(Instant.class)
        )).thenReturn(1);
        when(twilioCommandService.handle(request)).thenReturn(expected);

        TwilioWebhookResponseDto response = service.handle(request);

        assertThat(response).isSameAs(expected);
        verify(twilioCommandService).handle(request);
    }

    @Test
    void doesNotHandleCommandWhenConcurrentRequestWinsSidInsert() {
        TwilioWebhookRequestDto request = request();
        UserId userId = UserId.random();
        when(inboundMessageRepository.findByTwilioMessageSid(request.getMessageSid()))
                .thenReturn(Optional.empty());
        when(userIdentityService.resolveOrCreate(request.getFrom())).thenReturn(userId);
        when(twilioCommandService.classify(request)).thenReturn(InboundMessageEntity.CommandType.HELP);
        when(inboundMessageRepository.insertIfAbsent(
                any(UUID.class), eq(request.getMessageSid()), eq(userId.value()), eq("HELP"),
                any(Instant.class), any(Instant.class)
        )).thenReturn(0);

        TwilioWebhookResponseDto response = service.handle(request);

        assertThat(response.reply()).isEqualTo(TwilioWebhookServiceImpl.DUPLICATE_MESSAGE_RESPONSE);
        verify(twilioCommandService, never()).handle(any());
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
