package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.TwilioCommandHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class HelpTwilioCommandHandler implements TwilioCommandHandler {

    static final String HELP_RESPONSE = """
            WhatsAnki commands:
            *HELP*
            *SETTINGS*
            *LANGUAGE* <source> <definition>
            *ADD* <text>
            *LOOKUP* <word or phrase>
            *SAVE* <number>
            *SOURCES*
            *DELETE SOURCE* <source-id>
            *EXPORT* [deck name]
            *STATUS*
            *DELETE MY DATA*
            Send a text-layer PDF
            Use *ADD* <text> for pasted text""";

    @Override
    public InboundMessageEntity.CommandType supportedCommand() {
        return InboundMessageEntity.CommandType.HELP;
    }

    @Override
    @Transactional(readOnly = true)
    public TwilioWebhookResponseDto handle(TwilioCommandContextDto context) {
        log.info("Processed HELP command userId={}", context.userId().value());
        return new TwilioWebhookResponseDto(HELP_RESPONSE);
    }
}
