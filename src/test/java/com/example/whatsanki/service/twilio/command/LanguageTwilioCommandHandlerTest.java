package com.example.whatsanki.service.twilio.command;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LanguagePairUpdateResultDto;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.service.UserLanguageSettingsService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LanguageTwilioCommandHandlerTest {

    private final UserLanguageSettingsService userLanguageSettingsService =
            mock(UserLanguageSettingsService.class);
    private final LanguageTwilioCommandHandler handler =
            new LanguageTwilioCommandHandler(userLanguageSettingsService);

    @Test
    void registersForLanguageCommand() {
        assertThat(handler.supportedCommand()).isEqualTo(InboundMessageEntity.CommandType.LANGUAGE);
    }

    @Test
    void updatesUsingCanonicalParserValueAndExplainsExistingSourceBehavior() {
        TwilioCommandContextDto context = context("es-Latn-US en-US");
        LanguagePairDto pair = new LanguagePairDto("es-Latn-US", "en-US");
        when(userLanguageSettingsService.update(context.userId(), "es-Latn-US", "en-US"))
                .thenReturn(result(LanguagePairUpdateResultDto.Outcome.UPDATED, pair, List.of(pair)));

        assertThat(handler.handle(context).reply()).isEqualTo("""
                Language pair updated:
                Source language: es-Latn-US
                Definition language: en-US

                Existing sources keep their original language.""");
        verify(userLanguageSettingsService).update(context.userId(), "es-Latn-US", "en-US");
    }

    @Test
    void reportsAlreadyActivePairWithoutClaimingAnUpdate() {
        TwilioCommandContextDto context = context("es en");
        LanguagePairDto pair = new LanguagePairDto("es", "en");
        when(userLanguageSettingsService.update(context.userId(), "es", "en"))
                .thenReturn(result(LanguagePairUpdateResultDto.Outcome.UNCHANGED, pair, List.of(pair)));

        assertThat(handler.handle(context).reply()).startsWith("That language pair is already active:");
    }

    @Test
    void listsSupportedPairsWhenRequestedPairIsUnsupported() {
        TwilioCommandContextDto context = context("fr de");
        LanguagePairDto requested = new LanguagePairDto("fr", "de");
        List<LanguagePairDto> supported = List.of(
                new LanguagePairDto("es", "en"),
                new LanguagePairDto("en", "es")
        );
        when(userLanguageSettingsService.update(context.userId(), "fr", "de"))
                .thenReturn(result(LanguagePairUpdateResultDto.Outcome.UNSUPPORTED, requested, supported));

        assertThat(handler.handle(context).reply()).isEqualTo("""
                Unsupported language pair: fr → de.
                Supported pairs:
                - es → en
                - en → es

                Use *LANGUAGE* <source> <definition>.""");
    }

    @Test
    void rejectsBrokenNormalizedParserContract() {
        TwilioCommandContextDto context = context("es");

        assertThatThrownBy(() -> handler.handle(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("two normalized tags");
    }

    private LanguagePairUpdateResultDto result(
            LanguagePairUpdateResultDto.Outcome outcome,
            LanguagePairDto pair,
            List<LanguagePairDto> supported
    ) {
        return new LanguagePairUpdateResultDto(outcome, pair, supported);
    }

    private TwilioCommandContextDto context(String normalizedValue) {
        return new TwilioCommandContextDto(
                UserId.random(),
                InboundMessageId.random(),
                new ParsedTwilioCommandDto(
                        InboundMessageEntity.CommandType.LANGUAGE,
                        normalizedValue,
                        normalizedValue,
                        null,
                        null
                )
        );
    }
}
