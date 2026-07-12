package com.example.whatsanki.service.impl;

import com.example.whatsanki.dto.TwilioWebhookRequestDto;
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
    void returnsHelpForCaseInsensitiveCommandWithWhitespace() {
        TwilioWebhookRequestDto request = requestWithBody("  help  ");

        String reply = service.handle(request).reply();

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
        TwilioWebhookRequestDto request = requestWithBody("something else");

        assertThat(service.handle(request).reply())
                .isEqualTo(HelpTwilioWebhookServiceImpl.UNKNOWN_COMMAND_RESPONSE);
    }

    @Test
    void returnsGuidanceForMissingBody() {
        TwilioWebhookRequestDto request = requestWithBody(null);

        assertThat(service.handle(request).reply())
                .isEqualTo(HelpTwilioWebhookServiceImpl.UNKNOWN_COMMAND_RESPONSE);
    }

    private TwilioWebhookRequestDto requestWithBody(String body) {
        TwilioWebhookRequestDto request = new TwilioWebhookRequestDto();
        request.setMessageSid("SM123");
        request.setFrom("whatsapp:+15550000001");
        request.setTo("whatsapp:+15550000002");
        request.setBody(body);
        return request;
    }
}
