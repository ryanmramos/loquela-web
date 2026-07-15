package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelpTwilioCommandHandlerTest {

    private final HelpTwilioCommandHandler handler = new HelpTwilioCommandHandler();

    @Test
    void declaresHelpAsSupportedCommand() {
        assertThat(handler.supportedCommand()).isEqualTo(InboundMessageEntity.CommandType.HELP);
    }

    @Test
    void returnsCompleteHelpResponse() {
        String reply = handler.handle(context()).reply();

        assertThat(reply)
                .startsWith("WhatsAnki commands:")
                .contains("HELP")
                .contains("LANGUAGE <source> <definition>")
                .contains("ADD <text>")
                .contains("LOOKUP <word or phrase>")
                .contains("EXPORT [deck name]")
                .contains("STATUS");
    }

    private TwilioCommandContextDto context() {
        ParsedTwilioCommandDto command = new ParsedTwilioCommandDto(
                InboundMessageEntity.CommandType.HELP,
                null,
                null,
                null,
                null
        );
        return new TwilioCommandContextDto(UserId.random(), command);
    }
}
