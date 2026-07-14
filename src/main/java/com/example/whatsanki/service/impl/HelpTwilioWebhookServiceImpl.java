package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.TwilioCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class HelpTwilioWebhookServiceImpl implements TwilioCommandService {

    static final String HELP_RESPONSE = """
            WhatsAnki commands:
            HELP
            SETTINGS
            LANGUAGE <source> <definition>
            ADD <text>
            LOOKUP <word or phrase>
            SAVE <number>
            SOURCES
            DELETE SOURCE <source-id>
            EXPORT [deck name]
            STATUS
            DELETE MY DATA
            Send a TXT, PDF, or EPUB document""";

    static final String UNKNOWN_COMMAND_RESPONSE =
            "Send HELP to see the supported commands. Use ADD <text> to save pasted text.";

    static final String NOT_IMPLEMENTED_RESPONSE =
            "That command is recognized but is coming in a later milestone.";

    @Override
    @Transactional(readOnly = true)
    public TwilioWebhookResponseDto handle(ParsedTwilioCommandDto command) {
        if (!command.isValid()) {
            log.info("Rejected invalid command commandType={}", command.commandType());
            return new TwilioWebhookResponseDto(command.validationError());
        }
        if (command.commandType() == InboundMessageEntity.CommandType.HELP) {
            log.info("Processed HELP command");
            return new TwilioWebhookResponseDto(HELP_RESPONSE);
        }
        if (command.commandType() != InboundMessageEntity.CommandType.UNKNOWN) {
            log.info("Received recognized command that is not implemented commandType={}", command.commandType());
            return new TwilioWebhookResponseDto(NOT_IMPLEMENTED_RESPONSE);
        }
        log.info("Received ambiguous or unknown command");
        return new TwilioWebhookResponseDto(UNKNOWN_COMMAND_RESPONSE);
    }
}
