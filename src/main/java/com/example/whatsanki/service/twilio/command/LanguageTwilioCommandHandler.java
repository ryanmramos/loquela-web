package com.example.whatsanki.service.twilio.command;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LanguagePairUpdateResultDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.TwilioCommandHandler;
import com.example.whatsanki.service.UserLanguageSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LanguageTwilioCommandHandler implements TwilioCommandHandler {

    private final UserLanguageSettingsService userLanguageSettingsService;

    @Override
    public InboundMessageEntity.CommandType supportedCommand() {
        return InboundMessageEntity.CommandType.LANGUAGE;
    }

    @Override
    public TwilioWebhookResponseDto handle(TwilioCommandContextDto context) {
        // The parser guarantees two canonical tags. Split the normalized value here
        // instead of reparsing display text, whose original casing is intentionally retained.
        String[] tags = context.command().normalizedValue().split(" ", -1);
        if (tags.length != 2) {
            throw new IllegalStateException("Valid LANGUAGE command did not contain two normalized tags");
        }

        LanguagePairUpdateResultDto result = userLanguageSettingsService.update(
                context.userId(), tags[0], tags[1]
        );
        log.info(
                "Processed LANGUAGE command userId={} outcome={} sourceLanguage={} definitionLanguage={}",
                context.userId().value(),
                result.outcome(),
                result.languagePair().sourceLanguage(),
                result.languagePair().definitionLanguage()
        );
        return switch (result.outcome()) {
            case UPDATED -> response("Language pair updated:", result.languagePair());
            case UNCHANGED -> response("That language pair is already active:", result.languagePair());
            case UNSUPPORTED -> unsupportedResponse(result);
        };
    }

    private TwilioWebhookResponseDto response(String heading, LanguagePairDto pair) {
        return new TwilioWebhookResponseDto("""
                %s
                Source language: %s
                Definition language: %s

                Existing sources keep their original language.""".formatted(
                heading,
                pair.sourceLanguage(),
                pair.definitionLanguage()
        ));
    }

    private TwilioWebhookResponseDto unsupportedResponse(LanguagePairUpdateResultDto result) {
        String supported = result.supportedPairs().stream()
                .map(pair -> "- " + pair.sourceLanguage() + " → " + pair.definitionLanguage())
                .collect(Collectors.joining("\n"));
        LanguagePairDto requested = result.languagePair();
        return new TwilioWebhookResponseDto("""
                Unsupported language pair: %s → %s.
                Supported pairs:
                %s

                Use *LANGUAGE* <source> <definition>.""".formatted(
                requested.sourceLanguage(),
                requested.definitionLanguage(),
                supported
        ));
    }
}
