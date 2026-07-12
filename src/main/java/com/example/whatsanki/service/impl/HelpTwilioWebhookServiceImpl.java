package com.example.whatsanki.service.impl;

import com.example.whatsanki.dto.TwilioWebhookRequestDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.TwilioWebhookService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.text.Normalizer;
import java.util.Locale;

@Service
@Slf4j
public class HelpTwilioWebhookServiceImpl implements TwilioWebhookService {

    static final String HELP_RESPONSE = """
            WhatsAnki commands:
            HELP
            SETTINGS
            LANGUAGE <source> <definition>
            ADD <text>
            LOOKUP <word or phrase>
            SAVE <number>
            SOURCES
            EXPORT [deck name]
            STATUS""";

    static final String UNKNOWN_COMMAND_RESPONSE =
            "Send HELP to see the supported commands. Use ADD <text> to save pasted text.";

    @Override
    @Transactional(readOnly = true)
    public TwilioWebhookResponseDto handle(TwilioWebhookRequestDto request) {
        String command = normalize(request.getBody());
        if ("HELP".equals(command)) {
            log.info("Processed HELP command");
            return new TwilioWebhookResponseDto(HELP_RESPONSE);
        }
        log.info("Received command that is not implemented in the current milestone");
        return new TwilioWebhookResponseDto(UNKNOWN_COMMAND_RESPONSE);
    }

    private String normalize(String body) {
        if (body == null) {
            return "";
        }
        return Normalizer.normalize(body, Normalizer.Form.NFKC)
                .strip()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }
}
