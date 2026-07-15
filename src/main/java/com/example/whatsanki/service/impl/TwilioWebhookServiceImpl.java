package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookRequestDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.service.TwilioCommandParser;
import com.example.whatsanki.service.TwilioCommandService;
import com.example.whatsanki.service.TwilioWebhookService;
import com.example.whatsanki.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioWebhookServiceImpl implements TwilioWebhookService {

    static final String DUPLICATE_MESSAGE_RESPONSE = "This message was already processed.";

    private final InboundMessageRepository inboundMessageRepository;
    private final UserIdentityService userIdentityService;
    private final TwilioCommandParser twilioCommandParser;
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
        ParsedTwilioCommandDto command = twilioCommandParser.parse(request);
        Instant receivedAt = Instant.now();
        Instant purgeAfter = receivedAt.plus(configuration.getRetention().getInboundBody());
        InboundMessageId inboundMessageId = InboundMessageId.random();

        int inserted = inboundMessageRepository.insertIfAbsent(
                inboundMessageId.value(),
                request.getMessageSid(),
                userId.value(),
                command.commandType().name(),
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
                command.commandType(),
                command.hasMedia()
        );
        return twilioCommandService.handle(new TwilioCommandContextDto(userId, inboundMessageId, command));
    }

    private TwilioWebhookResponseDto duplicateResponse() {
        return new TwilioWebhookResponseDto(DUPLICATE_MESSAGE_RESPONSE);
    }
}
