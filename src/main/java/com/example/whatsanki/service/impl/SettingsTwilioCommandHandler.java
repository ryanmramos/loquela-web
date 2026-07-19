package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.TwilioCommandHandler;
import com.example.whatsanki.service.UserLanguageSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsTwilioCommandHandler implements TwilioCommandHandler {

    private final UserLanguageSettingsService userLanguageSettingsService;

    @Override
    public InboundMessageEntity.CommandType supportedCommand() {
        return InboundMessageEntity.CommandType.SETTINGS;
    }

    @Override
    public TwilioWebhookResponseDto handle(TwilioCommandContextDto context) {
        LanguagePairDto languagePair = userLanguageSettingsService.getCurrent(context.userId());
        log.info(
                "Processed SETTINGS command userId={} sourceLanguage={} definitionLanguage={}",
                context.userId().value(),
                languagePair.sourceLanguage(),
                languagePair.definitionLanguage()
        );
        return new TwilioWebhookResponseDto("""
                Current language pair:
                Source language: %s
                Definition language: %s

                Change it with LANGUAGE <source> <definition>.""".formatted(
                languagePair.sourceLanguage(),
                languagePair.definitionLanguage()
        ));
    }
}
