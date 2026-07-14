package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.TwilioWebhookRequestDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.service.TwilioCommandService;
import com.example.whatsanki.service.TwilioWebhookService;
import com.example.whatsanki.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioWebhookServiceImpl implements TwilioWebhookService {

    static final String DUPLICATE_MESSAGE_RESPONSE = "This message was already processed.";

    private final InboundMessageRepository inboundMessageRepository;
    private final UserIdentityService userIdentityService;
    private final TwilioCommandService twilioCommandService;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    @Transactional
    public TwilioWebhookResponseDto handle(TwilioWebhookRequestDto request) {
        // A fast lookup avoids identity work and command side effects for ordinary
        // Twilio retries. The conflict-safe insert below also covers concurrent retries.
        if (inboundMessageRepository.findByTwilioMessageSid(request.getMessageSid()).isPresent()) {
            log.info("Ignored duplicate Twilio webhook messageSid={}", request.getMessageSid());
            return duplicateResponse();
        }

        UserId userId = userIdentityService.resolveOrCreate(request.getFrom());
        InboundMessageEntity.CommandType commandType = twilioCommandService.classify(request);
        Instant receivedAt = Instant.now();
        Instant purgeAfter = receivedAt.plus(configuration.getRetention().getInboundBody());

        int inserted = inboundMessageRepository.insertIfAbsent(
                UUID.randomUUID(),
                request.getMessageSid(),
                userId.value(),
                commandType.name(),
                receivedAt,
                purgeAfter
        );
        if (inserted == 0) {
            log.info("Ignored concurrent duplicate Twilio webhook messageSid={}", request.getMessageSid());
            return duplicateResponse();
        }

        log.info(
                "Recorded inbound Twilio webhook messageSid={} userId={} commandType={} hasMedia={}",
                request.getMessageSid(),
                userId.value(),
                commandType,
                request.getNumMedia() > 0
        );
        return twilioCommandService.handle(request);
    }

    private TwilioWebhookResponseDto duplicateResponse() {
        return new TwilioWebhookResponseDto(DUPLICATE_MESSAGE_RESPONSE);
    }
}
