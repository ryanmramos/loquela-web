package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelpTwilioWebhookServiceImplTest {

    private HelpTwilioWebhookServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HelpTwilioWebhookServiceImpl();
    }

    @Test
    void returnsHelpForParsedHelpCommand() {
        ParsedTwilioCommandDto command = command(InboundMessageEntity.CommandType.HELP, null);

        String reply = service.handle(command).reply();

        assertThat(reply)
                .startsWith("WhatsAnki commands:")
                .contains("HELP")
                .contains("LANGUAGE <source> <definition>")
                .contains("ADD <text>")
                .contains("LOOKUP <word or phrase>")
                .contains("EXPORT [deck name]");
    }

    @Test
    void returnsGuidanceForUnknownCommand() {
        ParsedTwilioCommandDto command = command(InboundMessageEntity.CommandType.UNKNOWN, null);

        assertThat(service.handle(command).reply())
                .isEqualTo(HelpTwilioWebhookServiceImpl.UNKNOWN_COMMAND_RESPONSE);
    }

    @Test
    void returnsValidationMessageWithoutHandlingInvalidCommand() {
        ParsedTwilioCommandDto command = command(InboundMessageEntity.CommandType.ADD, "ADD requires text.");

        assertThat(service.handle(command).reply()).isEqualTo("ADD requires text.");
    }

    @Test
    void returnsMilestoneMessageForRecognizedUnsupportedCommand() {
        ParsedTwilioCommandDto command = command(InboundMessageEntity.CommandType.LOOKUP, null);

        assertThat(service.handle(command).reply())
                .isEqualTo(HelpTwilioWebhookServiceImpl.NOT_IMPLEMENTED_RESPONSE);
    }

    private ParsedTwilioCommandDto command(InboundMessageEntity.CommandType type, String validationError) {
        return new ParsedTwilioCommandDto(type, null, null, null, validationError);
    }
}
